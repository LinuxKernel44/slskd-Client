# Slskd Client — project notes for Claude

Native Android remote control for slskd (Kotlin, Compose, Kyant0 AndroidLiquidGlass). Package `com.linuxkernel44.slskdclient`.
Public repo `LinuxKernel44/slskd-Client`, branch `main`. Chat with David in French, everything in the repo in English (US).

## Layout
- `app/` — the Android app. `data/` (models, `ApiClient` REST, `Hub` SignalR-over-WebSocket, `Profiles`/`Secrets` encrypted
  storage, `Settings`, `Graph` singleton), `service/` (notification watcher), `player/` (Media3), `save/` (copy to phone),
  `update/` (GitHub Releases updater), `ui/` (theme, glass components, screens).
- `server/` — `slskd-files` companion (read-only file server, stdlib Python). Installed on `lea` as `slskd-files.service`,
  port 15501, ufw rules on `wg-lea-relay` and `tailscale0`. It validates the caller's slskd JWT against slskd itself.

## Facts worth knowing
- slskd's API has **no endpoint that returns file content**; hence the companion server. File paths in `/files/{root}/directories/{b64}`
  listings are relative to the listed directory (`fullName` is NOT the root-relative path) — the app builds `a/b/c` itself.
- The web UI source is recoverable from `/opt/slskd/wwwroot/static/js/main.*.js.map` (sourcesContent) — the best API reference.
- Test server: `http://10.0.0.8:15500` (slskd "perso" on lea). The web UI login is `SLSKD_USERNAME`/`SLSKD_PASSWORD` in
  `/etc/slskd-perso.env` (root only). Never write it into the repo.
- Signing: keystore `~/keystores/slskd-client-release.jks` (+ `.PASSWORDS.txt`), referenced by git-ignored `keystore.properties`.
  **Never regenerate the key** (updates must install over previous versions) and never commit it. Back it up.
- Build: JDK 17 (run with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`; `local.properties` has `sdk.dir`), `./gradlew --no-daemon`; the host has 10 GB RAM shared with
  many services — stop Gradle/Kotlin daemons afterwards (`./gradlew --stop`).
- Release flow: bump `versionCode`/`versionName` in `app/build.gradle.kts`, `./gradlew :app:assembleRelease`, verify with `apksigner verify`,
  `gh release create vX.Y.Z app/build/outputs/apk/release/app-release.apk#slskd-client-X.Y.Z.apk`. The in-app updater expects an `.apk`
  asset on the latest release and tags named `vX.Y.Z`.

## Session log
- 2026-10-28 — Initial implementation: all screens, companion server installed on lea, signing key created, v1.0.0.
- 2026-10-28 — Replaced the aurora with a Christmas background (firs, snow, string lights) in `Shell.kt` (`AuroraBackground`) and a green/red/gold palette in `Theme.kt`; tested on the S10 (debug build). Not released yet.
