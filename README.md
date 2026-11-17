# Slskd Client

A native Android remote control for [slskd](https://github.com/slskd/slskd), the Soulseek daemon. It talks to slskd's
REST API (no WebView) and covers everything the web UI does, in a Liquid Glass interface built with Jetpack Compose and
[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

## Features

- **Servers**: any number of slskd profiles with custom URL, login, **Remember me** (password and token encrypted with
  the Android Keystore), silent re-login, optional self-signed HTTPS certificates.
- **Search**: create searches (with limits), live results, the web UI's filter syntax (`minbr:320 islossless -live`),
  sorting, free-slot filter, per-file selection, folder/all download, browse a result's user.
- **Transfers**: downloads and uploads grouped by user and folder, live progress, cancel / retry / remove,
  queue position, clear finished, retry failed.
- **Social**: private messages, rooms (join, chat, member list), user lookup with info, status and picture,
  full share **browser** with folder download.
- **Library**: a full **file manager** over every folder slskd scans (Music, Downloads) plus Incomplete: browse, sort, search,
  multi-select, rename, copy / move, new folder, **upload from the phone**, delete to a recoverable **trash**, **ZIP** or save
  to phone, share / open with, image preview, audio tags (FLAC, MP3...), **play** (Media3, background playback with queue);
  shares are rescanned (and Navidrome for Music) after changes. Also manages slskd shares and rescans.
- **System**: server state, connect / disconnect, restart, **YAML options editor** with validation, live
  logs (SignalR) and log files, event feed.
- **Notifications** for finished downloads and new private messages (foreground service, optional), share-to-search
  (share text from any app to start a search), update check against GitHub Releases.

## Install

Download the signed `slskd-client-<version>.apk` from [Releases](../../releases) and install it (Android 8.0+).
Updates are signed with the same key, so they install over the previous version.

## File manager (companion server)

slskd's API can list and delete the files in its downloads folder but cannot return their content, and it knows nothing about
the other shared folders. The `server/` folder contains the companion (`slskd-files`, Python standard library only) that browses,
streams, zips, uploads, renames, moves, copies and deletes (to a trash) inside a fixed set of root folders, authenticated with
the **same slskd login token** (any valid slskd account may modify files).

```bash
sudo ./server/install.sh      # installs /opt/slskd-files + a systemd unit listening on :15501
# then allow the port from wherever the phone connects (VPN interface, LAN, ...)
```

One-time host setup for write access (see the header of `server/install.sh`): the music folder must be group-writable by a group
the service belongs to (`navidrome` here), and the trash directory (`/srv/slskd-files-trash`, same filesystem as the roots) must
exist. Roots, port, trash location and retention are set with environment variables in the unit (`SLSKD_FILES_ROOTS`,
`SLSKD_FILES_PORT`, `SLSKD_FILES_TRASH`, `SLSKD_FILES_TRASH_DAYS`). Optional `NAVIDROME_USER` / `NAVIDROME_PASSWORD` in
`/etc/slskd-files.env` let the app rescan Navidrome after music changes. In the app the file server defaults to the slskd host on
port 15501 and can be overridden per server under *Advanced*. Without the companion everything else still works; only the file
manager, playing and saving are disabled.

## Build

```bash
# JDK 17, Android SDK platform 37; set JAVA_HOME to a JDK 17 and sdk.dir in local.properties
./gradlew :app:assembleDebug
```

Release signing reads `keystore.properties` (git-ignored) at the repo root:

```properties
storeFile=/absolute/path/to/slskd-client-release.jks
storePassword=...
keyAlias=slskd-client
keyPassword=...
```

then `./gradlew :app:assembleRelease`. The keystore is never committed.

## License

MIT for this project. AndroidLiquidGlass is Apache-2.0 (see `LICENSE-AndroidLiquidGlass.txt`).
