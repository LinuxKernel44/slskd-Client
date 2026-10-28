#!/usr/bin/env bash
# Installs the slskd-files companion server (run as root: sudo ./install.sh).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
install -d -m 0755 /opt/slskd-files
install -m 0755 "$here/slskd-files-server.py" /opt/slskd-files/slskd-files-server.py
install -m 0644 "$here/slskd-files.service" /etc/systemd/system/slskd-files.service
systemctl daemon-reload
systemctl enable --now slskd-files.service
echo "Installed. Remember to allow the port, e.g.:"
echo "  ufw allow in on wg-lea-relay to any port 15501 proto tcp comment 'slskd-files via relay'"
