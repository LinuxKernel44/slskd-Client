#!/usr/bin/env bash
# Installs the slskd-files companion server (run as root: sudo ./install.sh).
#
# One-time prerequisites on the host (see the repo README):
#   * the music folder must be group-writable by the `navidrome` group (slskdperso is a member):
#       find /srv/musique ! -type l ! -group navidrome -exec chgrp navidrome {} +
#       find /srv/musique -type d -exec chmod g+rwxs {} + ; find /srv/musique -type f -exec chmod g+rw {} +
#   * the trash directory:  install -d -o slskdperso -g slskdperso -m 0750 /srv/slskd-files-trash
#   * optional /etc/slskd-files.env (root:slskdperso 0640) with NAVIDROME_USER=... NAVIDROME_PASSWORD=...
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
install -d -m 0755 /opt/slskd-files
install -d -o slskdperso -g slskdperso -m 0750 /srv/slskd-files-trash
install -m 0755 "$here/slskd-files-server.py" /opt/slskd-files/slskd-files-server.py
install -m 0644 "$here/slskd-files.service" /etc/systemd/system/slskd-files.service
systemctl daemon-reload
systemctl enable slskd-files.service
systemctl restart slskd-files.service
echo "Installed. Remember to allow the port, e.g.:"
echo "  ufw allow in on wg-lea-relay to any port 15501 proto tcp comment 'slskd-files via relay'"
