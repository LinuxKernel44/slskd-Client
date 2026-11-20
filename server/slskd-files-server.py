#!/usr/bin/env python3
"""File manager companion for slskd (used by the Slskd Client Android app).

slskd's HTTP API can list and delete files in its downloads directory but cannot return their
content, and knows nothing about the other shared folders. This small stdlib-only server fills
that gap: it browses, streams, zips, uploads, renames, moves, copies and deletes (to a
recoverable trash) inside a fixed set of root folders.

Security model:
  * Only the configured roots are reachable; path traversal and symlink escapes are rejected.
  * Every request (except /health) must carry the same `Authorization: Bearer <slskd JWT>` the app
    already uses for slskd; the token is validated against slskd itself (GET /api/v0/session),
    so there is no second credential to manage. Any valid slskd account may modify files.
  * Deleting never unlinks: items go to TRASH_DIR (same filesystem, outside every root) and are
    purged after TRASH_DAYS days.
  * Bind/ports/paths come from environment variables (see slskd-files.service).

API (v2): /health, /fm/roots, /fm/list, /fm/search, /fm/info, /fm/file, /fm/zip, /fm/mkdir, /fm/rename,
/fm/transfer, /fm/delete, /fm/trash, /fm/trash/restore, /fm/trash/purge, /fm/upload, /fm/rescan.
The v1 endpoint /files/downloads/<base64 path> is kept for older app versions.
"""
import base64
import binascii
import hashlib
import http.server
import json
import mimetypes
import os
import re
import secrets
import shutil
import socketserver
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import zipfile
from urllib.parse import parse_qs, quote, unquote, urlencode, urlsplit

BIND = os.environ.get("SLSKD_FILES_BIND", "0.0.0.0")
PORT = int(os.environ.get("SLSKD_FILES_PORT", "15501"))
SLSKD_API = os.environ.get("SLSKD_FILES_API", "http://127.0.0.1:15500/api/v0")
NAVIDROME_URL = os.environ.get("NAVIDROME_URL", "http://127.0.0.1:4533")
NAVIDROME_USER = os.environ.get("NAVIDROME_USER", "")
NAVIDROME_PASSWORD = os.environ.get("NAVIDROME_PASSWORD", "")
TRASH_DIR = os.path.realpath(os.environ.get("SLSKD_FILES_TRASH", "/srv/slskd-files-trash"))
TRASH_DAYS = int(os.environ.get("SLSKD_FILES_TRASH_DAYS", "30"))
# "id=name=/path;..." (the legacy SLSKD_FILES_DIR still defines the downloads root).
ROOTS_SPEC = os.environ.get(
    "SLSKD_FILES_ROOTS",
    "music=Music=/srv/musique;downloads=Downloads=" + os.environ.get("SLSKD_FILES_DIR", "/home/slskdperso/downloads")
    + ";incomplete=Incomplete=/home/slskdperso/incomplete",
)
AUTH_TTL = 60  # seconds a validated token is trusted before re-checking with slskd
CHUNK = 256 * 1024
MIN_FREE_AFTER_UPLOAD = 512 * 1024 * 1024
VERSION = 2

mimetypes.add_type("audio/flac", ".flac")
mimetypes.add_type("audio/ogg", ".opus")
mimetypes.add_type("audio/mp4", ".m4a")

ROOTS = {}  # id -> {"name", "path"}
for spec in filter(None, ROOTS_SPEC.split(";")):
    rid, rname, rpath = spec.split("=", 2)
    ROOTS[rid] = {"name": rname, "path": os.path.realpath(rpath)}

_auth_cache = {}
_auth_lock = threading.Lock()
_write_lock = threading.Lock()  # serializes mutations (move/copy/delete/restore/rename)
_range_re = re.compile(r"^bytes=(\d*)-(\d*)$")


_SIZE_CACHE = {"at": 0.0, "value": None}


def shared_size():
    """Total bytes of the roots slskd shares (music + downloads); cached for two minutes."""
    now = time.time()
    if _SIZE_CACHE["value"] is not None and now - _SIZE_CACHE["at"] < 120:
        return _SIZE_CACHE["value"]
    total = files = 0
    for rid in ("music", "downloads"):
        root = ROOTS.get(rid)
        if not root:
            continue
        for dirpath, _dirs, names in os.walk(root["path"]):
            for name in names:
                try:
                    total += os.lstat(os.path.join(dirpath, name)).st_size
                    files += 1
                except OSError:
                    pass
    _SIZE_CACHE.update(at=now, value={"bytes": total, "files": files})
    return _SIZE_CACHE["value"]


class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def token_valid(header):
    """Validates an Authorization header against slskd, with a short positive cache."""
    if not header or not header.lower().startswith("bearer "):
        return False
    now = time.monotonic()
    with _auth_lock:
        expires = _auth_cache.get(header)
        if expires and expires > now:
            return True
        for key in [k for k, v in _auth_cache.items() if v <= now]:
            del _auth_cache[key]
    request = urllib.request.Request(f"{SLSKD_API}/session", headers={"Authorization": header})
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            ok = response.status == 200
    except (urllib.error.URLError, OSError):
        ok = False
    if ok:
        with _auth_lock:
            _auth_cache[header] = now + AUTH_TTL
    return ok


# ---------------------------------------------------------------- paths

def inside(path, base):
    return path == base or path.startswith(base + os.sep)


def parse_rel(rel):
    parts = [p for p in (rel or "").replace("\\", "/").split("/") if p not in ("", ".")]
    if any(p == ".." for p in parts):
        raise ApiError(400, "bad path")
    return parts


def valid_name(name):
    if not name or name in (".", "..") or "/" in name or "\\" in name or "\0" in name or len(name.encode()) > 255:
        raise ApiError(400, "invalid name")
    return name


def locate(root_id, rel, must_exist=True):
    """Resolves (root, relative path) to (absolute path, parts). Symlinks are never followed past the root."""
    root = ROOTS.get(root_id)
    if not root or not os.path.isdir(root["path"]):
        raise ApiError(404, "unknown root")
    base = root["path"]
    parts = parse_rel(rel)
    if not parts:
        return base, parts
    parent = os.path.realpath(os.path.join(base, *parts[:-1]))
    if not inside(parent, base):
        raise ApiError(400, "bad path")
    full = os.path.join(parent, parts[-1])
    if must_exist and not os.path.lexists(full):
        raise ApiError(404, "not found")
    return full, parts


def locate_file(root_id, rel):
    full, parts = locate(root_id, rel)
    real = os.path.realpath(full)
    if not inside(real, ROOTS[root_id]["path"]) or not os.path.isfile(real):
        raise ApiError(404, "not found")
    return real


def locate_dir(root_id, rel):
    full, parts = locate(root_id, rel)
    real = os.path.realpath(full)
    if not inside(real, ROOTS[root_id]["path"]) or not os.path.isdir(real):
        raise ApiError(404, "folder not found")
    return real


def unique_name(directory, name, is_dir):
    if not os.path.lexists(os.path.join(directory, name)):
        return name
    stem, ext = (name, "") if is_dir else os.path.splitext(name)
    n = 1
    while os.path.lexists(os.path.join(directory, f"{stem} ({n}){ext}")):
        n += 1
    return f"{stem} ({n}){ext}"


def entry_json(entry):
    try:
        st = entry.stat()
        is_dir = entry.is_dir()
    except OSError:
        try:
            st = entry.stat(follow_symlinks=False)
            is_dir = False
        except OSError:
            return None
    return {"name": entry.name, "dir": is_dir, "size": 0 if is_dir else st.st_size, "mtime": int(st.st_mtime), "link": entry.is_symlink()}


def tree_stats(path, cap=200000, budget=20.0):
    """Returns (files, dirs, bytes, truncated) for a folder."""
    files = dirs = size = seen = 0
    deadline = time.monotonic() + budget
    truncated = False
    for current, subdirs, names in os.walk(path):
        dirs += len(subdirs)
        for name in names:
            files += 1
            try:
                size += os.lstat(os.path.join(current, name)).st_size
            except OSError:
                pass
        seen += len(names) + len(subdirs)
        if seen > cap or time.monotonic() > deadline:
            truncated = True
            break
    return files, dirs, size, truncated


def audio_info(path):
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", path],
            capture_output=True, timeout=15, check=True,
        ).stdout
        data = json.loads(out)
    except (OSError, subprocess.SubprocessError, ValueError):
        return None
    audio = next((s for s in data.get("streams", []) if s.get("codec_type") == "audio"), None)
    if not audio:
        return None
    fmt = data.get("format", {})
    tags = {k.lower(): v for k, v in {**audio.get("tags", {}), **fmt.get("tags", {})}.items()}
    pick = lambda *keys: next((tags[k] for k in keys if k in tags), None)
    return {
        "codec": audio.get("codec_name"),
        "duration": float(fmt["duration"]) if fmt.get("duration") else None,
        "bitrate": int(fmt["bit_rate"]) if fmt.get("bit_rate") else None,
        "sampleRate": int(audio["sample_rate"]) if audio.get("sample_rate") else None,
        "channels": audio.get("channels"),
        "bitDepth": int(audio.get("bits_per_raw_sample") or audio.get("bits_per_sample") or 0) or None,
        "title": pick("title"), "artist": pick("artist"), "album": pick("album"),
        "albumArtist": pick("album_artist", "albumartist"), "date": pick("date", "year"),
        "track": pick("track", "tracknumber"), "disc": pick("disc", "discnumber"), "genre": pick("genre"),
    }


# ---------------------------------------------------------------- trash

def trash_item(root_id, parts, full):
    is_dir = os.path.isdir(full) and not os.path.islink(full)
    if is_dir:
        _, _, size, _ = tree_stats(full, cap=50000, budget=5.0)
    else:
        try:
            size = os.lstat(full).st_size
        except OSError:
            size = 0
    item_id = f"{int(time.time())}-{secrets.token_hex(4)}"
    holder = os.path.join(TRASH_DIR, item_id)
    os.makedirs(holder)
    shutil.move(full, os.path.join(holder, "item"))
    meta = {"id": item_id, "root": root_id, "path": "/".join(parts), "name": parts[-1], "dir": is_dir, "size": size, "deleted": int(time.time())}
    with open(os.path.join(holder, "meta.json"), "w") as handle:
        json.dump(meta, handle)
    return meta


def trash_list():
    items = []
    if not os.path.isdir(TRASH_DIR):
        return items
    for item_id in os.listdir(TRASH_DIR):
        try:
            with open(os.path.join(TRASH_DIR, item_id, "meta.json")) as handle:
                items.append(json.load(handle))
        except (OSError, ValueError):
            continue
    return sorted(items, key=lambda m: -m.get("deleted", 0))


def trash_holder(item_id):
    if not re.fullmatch(r"[0-9]+-[0-9a-f]{8}", item_id or ""):
        raise ApiError(400, "bad trash id")
    holder = os.path.join(TRASH_DIR, item_id)
    if not os.path.isdir(holder):
        raise ApiError(404, "trash item not found")
    return holder


def purge_holder(holder):
    shutil.rmtree(holder, ignore_errors=True)


def purge_expired():
    cutoff = time.time() - TRASH_DAYS * 86400
    for meta in trash_list():
        if meta.get("deleted", 0) < cutoff:
            with _write_lock:
                purge_holder(os.path.join(TRASH_DIR, meta["id"]))


def purge_loop():
    while True:
        try:
            purge_expired()
        except Exception as exc:  # noqa: BLE001 - housekeeping must never kill the thread
            sys.stderr.write(f"trash purge failed: {exc}\n")
        time.sleep(3600)


# ---------------------------------------------------------------- mutations

def make_dir(root_id, rel, name):
    parent = locate_dir(root_id, rel)
    target = os.path.join(parent, valid_name(name))
    if os.path.lexists(target):
        raise ApiError(409, "already exists")
    os.mkdir(target)
    return {"name": name}


def rename(root_id, rel, name):
    full, parts = locate(root_id, rel)
    if not parts:
        raise ApiError(400, "cannot rename a root")
    target = os.path.join(os.path.dirname(full), valid_name(name))
    if target == full:
        return {"name": name}
    if os.path.lexists(target):
        raise ApiError(409, "already exists")
    os.rename(full, target)
    return {"name": name}


def transfer(mode, items, dest, policy):
    dest_dir = locate_dir(dest.get("root"), dest.get("path", ""))
    done, skipped, errors = 0, 0, []
    for item in items:
        label = item.get("path", "")
        try:
            src, parts = locate(item.get("root"), item.get("path", ""))
            if not parts:
                raise ApiError(400, "cannot move or copy a root")
            is_dir = os.path.isdir(src) and not os.path.islink(src)
            if is_dir and (dest_dir == src or dest_dir.startswith(src + os.sep)):
                raise ApiError(400, "cannot put a folder inside itself")
            name = parts[-1]
            target = os.path.join(dest_dir, name)
            if target == src:
                if mode == "move":
                    skipped += 1
                    continue
                target = os.path.join(dest_dir, unique_name(dest_dir, name, is_dir))
            elif os.path.lexists(target):
                if policy == "skip":
                    skipped += 1
                    continue
                if policy == "replace":
                    existing_parts = parse_rel(os.path.relpath(target, ROOTS[dest["root"]]["path"]))
                    trash_item(dest["root"], existing_parts, target)
                else:
                    target = os.path.join(dest_dir, unique_name(dest_dir, name, is_dir))
            if mode == "copy":
                if is_dir:
                    shutil.copytree(src, target, symlinks=True)
                else:
                    shutil.copy2(src, target, follow_symlinks=False)
            else:
                shutil.move(src, target)
            done += 1
        except ApiError as exc:
            errors.append({"path": label, "error": exc.message})
        except OSError as exc:
            errors.append({"path": label, "error": exc.strerror or str(exc)})
    return {"done": done, "skipped": skipped, "errors": errors}


def delete_items(items):
    done, errors = 0, []
    for item in items:
        label = item.get("path", "")
        try:
            full, parts = locate(item.get("root"), label)
            if not parts:
                raise ApiError(400, "cannot delete a root")
            trash_item(item["root"], parts, full)
            done += 1
        except ApiError as exc:
            errors.append({"path": label, "error": exc.message})
        except OSError as exc:
            errors.append({"path": label, "error": exc.strerror or str(exc)})
    return {"done": done, "errors": errors}


def restore_items(ids, policy):
    done, errors = 0, []
    for item_id in ids:
        try:
            holder = trash_holder(item_id)
            with open(os.path.join(holder, "meta.json")) as handle:
                meta = json.load(handle)
            base = ROOTS[meta["root"]]["path"]
            parts = parse_rel(meta["path"])
            parent = os.path.realpath(os.path.join(base, *parts[:-1]))
            if not inside(parent, base):
                raise ApiError(400, "bad path")
            os.makedirs(parent, exist_ok=True)
            name = parts[-1]
            target = os.path.join(parent, name)
            if os.path.lexists(target):
                if policy == "skip":
                    continue
                name = unique_name(parent, name, meta.get("dir", False))
                target = os.path.join(parent, name)
            shutil.move(os.path.join(holder, "item"), target)
            purge_holder(holder)
            done += 1
        except (ApiError, KeyError) as exc:
            errors.append({"path": item_id, "error": getattr(exc, "message", str(exc))})
        except OSError as exc:
            errors.append({"path": item_id, "error": exc.strerror or str(exc)})
    return {"done": done, "errors": errors}


def search(root_id, rel, query, limit=300, budget=15.0):
    start = locate_dir(root_id, rel)
    base = ROOTS[root_id]["path"]
    needle = query.lower()
    results, deadline = [], time.monotonic() + budget
    for current, subdirs, names in os.walk(start):
        for name in subdirs + names:
            if needle in name.lower():
                full = os.path.join(current, name)
                try:
                    st = os.stat(full)
                except OSError:
                    continue
                is_dir = os.path.isdir(full)
                results.append({"name": name, "path": os.path.relpath(full, base), "dir": is_dir,
                                "size": 0 if is_dir else st.st_size, "mtime": int(st.st_mtime)})
                if len(results) >= limit:
                    return results, True
        if time.monotonic() > deadline:
            return results, True
    return results, False


def rescan(auth_header, want_slskd, want_navidrome):
    result = {}
    if want_slskd:
        try:
            request = urllib.request.Request(f"{SLSKD_API}/shares", method="PUT", headers={"Authorization": auth_header})
            with urllib.request.urlopen(request, timeout=10):
                result["slskd"] = "started"
        except (urllib.error.URLError, OSError) as exc:
            result["slskd"] = f"failed: {exc}"
    if want_navidrome:
        if not NAVIDROME_USER or not NAVIDROME_PASSWORD:
            result["navidrome"] = "not configured"
        else:
            salt = secrets.token_hex(6)
            query = urlencode({"u": NAVIDROME_USER, "t": hashlib.md5((NAVIDROME_PASSWORD + salt).encode()).hexdigest(),
                               "s": salt, "v": "1.16.1", "c": "slskd-files", "f": "json"})
            try:
                with urllib.request.urlopen(f"{NAVIDROME_URL}/rest/startScan.view?{query}", timeout=10) as response:
                    status = json.loads(response.read()).get("subsonic-response", {}).get("status")
                    result["navidrome"] = "started" if status == "ok" else "failed"
            except (urllib.error.URLError, OSError, ValueError) as exc:
                result["navidrome"] = f"failed: {exc}"
    return result


class ZipSink:
    """Write-only, unseekable sink so zipfile streams straight to the socket."""

    def __init__(self, wfile):
        self.wfile = wfile
        self.position = 0

    def write(self, data):
        self.wfile.write(data)
        self.position += len(data)
        return len(data)

    def tell(self):
        return self.position

    def flush(self):
        self.wfile.flush()


# ---------------------------------------------------------------- HTTP

class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "slskd-files/2"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep journald quiet but useful
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    # -- helpers
    def _json(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _body_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length > 4 * 1024 * 1024:
            raise ApiError(413, "body too large")
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            raise ApiError(400, "bad json")

    def _dispatch(self, handler):
        try:
            handler()
        except ApiError as exc:
            self.close_connection = self.command in ("PUT",) or self.close_connection
            self._json(exc.status, {"error": exc.message})
        except (BrokenPipeError, ConnectionResetError):
            self.close_connection = True
        except OSError as exc:
            self._json(500, {"error": exc.strerror or str(exc)})
        except Exception as exc:  # noqa: BLE001
            sys.stderr.write(f"unhandled: {exc!r}\n")
            self._json(500, {"error": "internal error"})

    def _authorized(self):
        return token_valid(self.headers.get("Authorization"))

    # -- verbs
    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        self._dispatch(self._get)

    def do_POST(self):
        self._dispatch(self._post)

    def do_PUT(self):
        self._dispatch(self._put)

    def _get(self):
        split = urlsplit(self.path)
        path, query = split.path, parse_qs(split.query, keep_blank_values=True)
        q = lambda key, default="": query.get(key, [default])[0]
        if path == "/health":
            return self._json(200, {"service": "slskd-files", "version": VERSION})
        if path.startswith("/files/downloads/"):
            return self._legacy_file(path)
        if not path.startswith("/fm/"):
            raise ApiError(404, "not found")
        if not self._authorized():
            raise ApiError(401, "unauthorized")
        route = path[4:]
        if route == "roots":
            roots = []
            for rid, root in ROOTS.items():
                if os.path.isdir(root["path"]):
                    usage = shutil.disk_usage(root["path"])
                    roots.append({"id": rid, "name": root["name"], "free": usage.free, "total": usage.total})
            return self._json(200, {"version": VERSION, "roots": roots, "trashDays": TRASH_DAYS,
                                    "navidrome": bool(NAVIDROME_USER and NAVIDROME_PASSWORD)})
        if route == "shared-size":
            return self._json(200, shared_size())
        if route == "list":
            directory = locate_dir(q("root"), q("path"))
            entries = []
            with os.scandir(directory) as scan:
                for entry in scan:
                    try:
                        entry.name.encode("utf-8")
                    except UnicodeEncodeError:
                        continue
                    item = entry_json(entry)
                    if item:
                        entries.append(item)
            return self._json(200, {"entries": entries})
        if route == "search":
            if not q("q").strip():
                raise ApiError(400, "empty query")
            results, truncated = search(q("root"), q("path"), q("q").strip())
            return self._json(200, {"entries": results, "truncated": truncated})
        if route == "info":
            full, parts = locate(q("root"), q("path"))
            real = os.path.realpath(full)
            if not inside(real, ROOTS[q("root")]["path"]):
                raise ApiError(404, "not found")
            st = os.stat(real)
            if os.path.isdir(real):
                files, dirs, size, truncated = tree_stats(real)
                return self._json(200, {"dir": True, "files": files, "dirs": dirs, "size": size, "truncated": truncated, "mtime": int(st.st_mtime)})
            mime = mimetypes.guess_type(real)[0] or "application/octet-stream"
            info = {"dir": False, "size": st.st_size, "mtime": int(st.st_mtime), "mime": mime}
            if mime.startswith("audio/"):
                info["audio"] = audio_info(real)
            return self._json(200, info)
        if route == "file":
            return self._serve(locate_file(q("root"), q("path")))
        if route == "zip":
            return self._zip(query)
        if route == "trash":
            return self._json(200, {"items": trash_list(), "days": TRASH_DAYS})
        raise ApiError(404, "not found")

    def _post(self):
        path = urlsplit(self.path).path
        if not path.startswith("/fm/"):
            raise ApiError(404, "not found")
        if not self._authorized():
            raise ApiError(401, "unauthorized")
        route, body = path[4:], self._body_json()
        policy = body.get("onConflict", "rename")
        if route == "mkdir":
            with _write_lock:
                return self._json(200, make_dir(body.get("root"), body.get("path", ""), body.get("name", "")))
        if route == "rename":
            with _write_lock:
                return self._json(200, rename(body.get("root"), body.get("path", ""), body.get("name", "")))
        if route == "transfer":
            if body.get("mode") not in ("copy", "move"):
                raise ApiError(400, "mode must be copy or move")
            with _write_lock:
                return self._json(200, transfer(body["mode"], body.get("items", []), body.get("dest", {}), policy))
        if route == "delete":
            with _write_lock:
                return self._json(200, delete_items(body.get("items", [])))
        if route == "trash/restore":
            with _write_lock:
                return self._json(200, restore_items(body.get("ids", []), policy))
        if route == "trash/purge":
            ids = [m["id"] for m in trash_list()] if body.get("all") else body.get("ids", [])
            with _write_lock:
                for item_id in ids:
                    purge_holder(trash_holder(item_id))
            return self._json(200, {"done": len(ids)})
        if route == "rescan":
            return self._json(200, rescan(self.headers.get("Authorization"), bool(body.get("slskd")), bool(body.get("navidrome"))))
        raise ApiError(404, "not found")

    def _put(self):
        split = urlsplit(self.path)
        if split.path != "/fm/upload":
            raise ApiError(404, "not found")
        if not self._authorized():
            raise ApiError(401, "unauthorized")
        query = parse_qs(split.query, keep_blank_values=True)
        q = lambda key, default="": query.get(key, [default])[0]
        name, policy = valid_name(q("name")), q("onConflict", "rename")
        directory = locate_dir(q("root"), q("path"))
        chunked = "chunked" in (self.headers.get("Transfer-Encoding") or "").lower()
        length = None if chunked else int(self.headers.get("Content-Length") or 0)
        if length is not None and shutil.disk_usage(directory).free < length + MIN_FREE_AFTER_UPLOAD:
            raise ApiError(507, "not enough free space")
        target = os.path.join(directory, name)
        if os.path.lexists(target):
            if policy == "skip":
                raise ApiError(409, "already exists")
            if policy == "rename":
                target = os.path.join(directory, unique_name(directory, name, False))
        part = os.path.join(directory, f".{os.path.basename(target)}.{secrets.token_hex(3)}.part")
        written = 0
        try:
            with open(part, "wb") as out:
                if chunked:
                    while True:
                        size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                        if size == 0:
                            self.rfile.readline()
                            break
                        remaining = size
                        while remaining:
                            data = self.rfile.read(min(CHUNK, remaining))
                            if not data:
                                raise ConnectionResetError
                            out.write(data)
                            remaining -= len(data)
                            written += len(data)
                        self.rfile.readline()
                else:
                    remaining = length
                    while remaining:
                        data = self.rfile.read(min(CHUNK, remaining))
                        if not data:
                            raise ConnectionResetError
                        out.write(data)
                        remaining -= len(data)
                        written += len(data)
            with _write_lock:
                if os.path.lexists(target) and policy == "replace":
                    base = ROOTS[q("root")]["path"]
                    trash_item(q("root"), parse_rel(os.path.relpath(target, base)), target)
                os.replace(part, target)
        except BaseException:
            try:
                os.unlink(part)
            except OSError:
                pass
            raise
        self._json(200, {"name": os.path.basename(target), "size": written})

    # -- file content
    def _legacy_file(self, path):
        if not self._authorized():
            raise ApiError(401, "unauthorized")
        try:
            relative = base64.b64decode(unquote(path[len("/files/downloads/"):]), validate=True).decode("utf-8")
        except (binascii.Error, UnicodeDecodeError, ValueError):
            raise ApiError(400, "bad path")
        self._serve(locate_file("downloads", relative))

    def _serve(self, target):
        size = os.path.getsize(target)
        start, end, status = 0, size - 1, 200
        header = self.headers.get("Range")
        if header:
            match = _range_re.match(header.strip())
            if not match or (not match.group(1) and not match.group(2)):
                raise ApiError(416, "bad range")
            if match.group(1):
                start = int(match.group(1))
                end = int(match.group(2)) if match.group(2) else size - 1
            else:  # suffix range
                start = max(size - int(match.group(2)), 0)
            end = min(end, size - 1)
            if start >= size or start > end:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{size}")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            status = 206
        length = end - start + 1 if size else 0
        mime = mimetypes.guess_type(target)[0] or "application/octet-stream"
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Disposition", "inline; filename*=UTF-8''" + quote(os.path.basename(target)))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if self.command == "HEAD" or length == 0:
            return
        with open(target, "rb") as handle:
            handle.seek(start)
            remaining = length
            while remaining > 0:
                data = handle.read(min(CHUNK, remaining))
                if not data:
                    break
                self.wfile.write(data)
                remaining -= len(data)

    def _zip(self, query):
        roots, paths = query.get("root", []), query.get("path", [])
        if not roots or len(roots) != len(paths):
            raise ApiError(400, "root and path are required, once per item")
        entries, estimate = [], 0
        for root_id, rel in zip(roots, paths):
            full, parts = locate(root_id, rel)
            if not parts:
                raise ApiError(400, "cannot zip a root")
            real = os.path.realpath(full)
            if not inside(real, ROOTS[root_id]["path"]):
                raise ApiError(404, "not found")
            entries.append((real, parts[-1]))
            if os.path.isdir(real):
                estimate += tree_stats(real, budget=5.0)[2]
            else:
                estimate += os.path.getsize(real)
        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("X-Zip-Estimate", str(estimate))
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        if self.command == "HEAD":
            return
        with zipfile.ZipFile(ZipSink(self.wfile), "w", zipfile.ZIP_STORED, allowZip64=True) as archive:
            for real, name in entries:
                if os.path.isdir(real):
                    archive.mkdir(name)
                    for current, subdirs, names in os.walk(real):
                        for sub in subdirs:
                            archive.mkdir(os.path.join(name, os.path.relpath(os.path.join(current, sub), real)))
                        for file_name in names:
                            file_path = os.path.join(current, file_name)
                            if os.path.isfile(file_path):
                                self._zip_file(archive, file_path, os.path.join(name, os.path.relpath(file_path, real)))
                else:
                    self._zip_file(archive, real, name)

    @staticmethod
    def _zip_file(archive, file_path, arcname):
        info = zipfile.ZipInfo.from_file(file_path, arcname)
        info.compress_type = zipfile.ZIP_STORED
        with open(file_path, "rb") as source, archive.open(info, "w", force_zip64=True) as sink:
            shutil.copyfileobj(source, sink, CHUNK)


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    os.umask(0o002)  # new files stay group-writable (music is shared with navidrome and ripultra)
    os.makedirs(TRASH_DIR, exist_ok=True)
    threading.Thread(target=purge_loop, daemon=True).start()
    print(f"serving {', '.join(r['path'] for r in ROOTS.values())} on {BIND}:{PORT}", flush=True)
    Server((BIND, PORT), Handler).serve_forever()
