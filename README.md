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
- **Library**: browse, delete, **play** (Media3, background playback with queue) and **save to phone** the files slskd
  downloaded; manage shares and rescans.
- **System**: server state, connect / disconnect, restart / shut down, **YAML options editor** with validation, live
  logs (SignalR) and log files, event feed.
- **Notifications** for finished downloads and new private messages (foreground service, optional), share-to-search
  (share text from any app to start a search), update check against GitHub Releases.

## Install

Download the signed `slskd-client-<version>.apk` from [Releases](../../releases) and install it (Android 8.0+).
Updates are signed with the same key, so they install over the previous version.

## Streaming and saving files (companion server)

slskd's API can list and delete the files in its downloads folder but cannot return their content. The `server/` folder
contains a tiny read-only companion (`slskd-files`, Python standard library only) that serves the downloads directory
over HTTP, authenticated with the **same slskd login token**:

```bash
sudo ./server/install.sh      # installs /opt/slskd-files + a systemd unit listening on :15501
# then allow the port from wherever the phone connects (VPN interface, LAN, ...)
```

Defaults can be changed with environment variables in the unit (`SLSKD_FILES_DIR`, `SLSKD_FILES_PORT`,
`SLSKD_FILES_BIND`, `SLSKD_FILES_API`). In the app the file server defaults to the slskd host on port 15501 and can be
overridden per server under *Advanced*. Without the companion everything else still works; only play/save is disabled.

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
