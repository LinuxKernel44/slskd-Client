#!/usr/bin/env python3
"""Read-only file server companion for slskd (used by the Slskd Client Android app).

slskd's HTTP API can list and delete files in the downloads directory but cannot
return their content. This tiny server fills that gap so the app can stream audio
and save files to the phone.

Security model:
  * Read-only, serves only inside DOWNLOADS_DIR (path traversal and symlink escapes rejected).
  * Every request must carry the same `Authorization: Bearer <slskd JWT>` the app already
    uses for slskd; the token is validated against slskd itself (GET /api/v0/session),
    so there is no second credential to manage.
  * Bind/ports/paths come from environment variables (see slskd-files.service).
"""
import base64
import binascii
import http.server
import json
import mimetypes
import os
import re
import socketserver
import sys
import threading
import time
import urllib.error
import urllib.request
from urllib.parse import unquote, urlsplit

DOWNLOADS_DIR = os.path.realpath(os.environ.get("SLSKD_FILES_DIR", "/home/slskdperso/downloads"))
BIND = os.environ.get("SLSKD_FILES_BIND", "0.0.0.0")
PORT = int(os.environ.get("SLSKD_FILES_PORT", "15501"))
SLSKD_API = os.environ.get("SLSKD_FILES_API", "http://127.0.0.1:15500/api/v0")
AUTH_TTL = 60  # seconds a validated token is trusted before re-checking with slskd
CHUNK = 256 * 1024
VERSION = 1

mimetypes.add_type("audio/flac", ".flac")
mimetypes.add_type("audio/ogg", ".opus")
mimetypes.add_type("audio/mp4", ".m4a")

_auth_cache = {}
_auth_lock = threading.Lock()
_range_re = re.compile(r"^bytes=(\d*)-(\d*)$")


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


def resolve(relative):
    """Maps a relative path to a real file inside DOWNLOADS_DIR, or None."""
    relative = relative.replace("\\", "/").lstrip("/")
    candidate = os.path.realpath(os.path.join(DOWNLOADS_DIR, relative))
    if candidate != DOWNLOADS_DIR and not candidate.startswith(DOWNLOADS_DIR + os.sep):
        return None
    return candidate if os.path.isfile(candidate) else None


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "slskd-files/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep journald quiet but useful
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _json(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            return self._json(200, {"service": "slskd-files", "version": VERSION})
        prefix = "/files/downloads/"
        if not path.startswith(prefix):
            return self._json(404, {"error": "not found"})
        if not token_valid(self.headers.get("Authorization")):
            return self._json(401, {"error": "unauthorized"})
        try:
            relative = base64.b64decode(unquote(path[len(prefix):]), validate=True).decode("utf-8")
        except (binascii.Error, UnicodeDecodeError, ValueError):
            return self._json(400, {"error": "bad path"})
        target = resolve(relative)
        if target is None:
            return self._json(404, {"error": "not found"})
        size = os.path.getsize(target)
        start, end, status = 0, size - 1, 200
        header = self.headers.get("Range")
        if header:
            match = _range_re.match(header.strip())
            if not match or (not match.group(1) and not match.group(2)):
                return self._json(416, {"error": "bad range"})
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
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if self.command == "HEAD" or length == 0:
            return
        try:
            with open(target, "rb") as handle:
                handle.seek(start)
                remaining = length
                while remaining > 0:
                    data = handle.read(min(CHUNK, remaining))
                    if not data:
                        break
                    self.wfile.write(data)
                    remaining -= len(data)
        except (BrokenPipeError, ConnectionResetError):
            pass


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    print(f"serving {DOWNLOADS_DIR} on {BIND}:{PORT}", flush=True)
    Server((BIND, PORT), Handler).serve_forever()
