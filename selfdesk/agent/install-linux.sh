#!/usr/bin/env bash
# SelfDesk agent setup for Linux desktops (X11 session). Runs as your user.
set -euo pipefail
cd "$(dirname "$0")"
python3 -m venv .venv
.venv/bin/pip install -q -r requirements.txt
if [ ! -f config.json ]; then
  read -rp "Server URL (e.g. https://desk.example.com): " SERVER
  read -rp "Enroll token: " TOKEN
  read -rp "Name for this PC: " NAME
  .venv/bin/python agent.py --server "$SERVER" --token "$TOKEN" --name "$NAME" --setup-only
fi
mkdir -p ~/.config/systemd/user
cat > ~/.config/systemd/user/selfdesk-agent.service <<UNIT
[Unit]
Description=SelfDesk agent
After=graphical-session.target
PartOf=graphical-session.target

[Service]
ExecStart=$PWD/.venv/bin/python $PWD/agent.py
Restart=always
RestartSec=5

[Install]
WantedBy=graphical-session.target
UNIT
systemctl --user daemon-reload
systemctl --user import-environment DISPLAY XAUTHORITY || true
systemctl --user enable --now selfdesk-agent.service
grep '"id"' config.json
echo "Done. Logs: journalctl --user -u selfdesk-agent -f"
