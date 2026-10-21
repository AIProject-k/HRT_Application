#!/data/data/com.termux/files/usr/bin/bash
set -eu
umask 077
mkdir -p "$HOME/.local/share/hormonelog-chat"
if [ -f "$HOME/.local/share/hormonelog-chat/job-pid" ] && kill -0 "$(cat "$HOME/.local/share/hormonelog-chat/job-pid")" 2>/dev/null; then
  printf '{"running":true,"phase":"termux"}'
  exit 0
fi
printf '%s' "$$" > "$HOME/.local/share/hormonelog-chat/job-pid"
printf '{"at":"%s","phase":"termux","error":""}\n' "$(date -u +%Y-%m-%dT%H:%M:%S+00:00)" >> "$HOME/.local/share/hormonelog-chat/events.jsonl"
# The Android app supplies a fixed, bundled payload, never conversation text.
if ! command -v python >/dev/null || ! command -v proot-distro >/dev/null || ! curl --version >/dev/null 2>&1; then
  TERMUX_PKG_NO_MIRROR_SELECT=1 pkg update -y > "$HOME/hrt-setup-packages.log" 2>&1 || pkg update -y >> "$HOME/hrt-setup-packages.log" 2>&1
  TERMUX_PKG_NO_MIRROR_SELECT=1 pkg upgrade -y -o Dpkg::Options::=--force-confold >> "$HOME/hrt-setup-packages.log" 2>&1
  TERMUX_PKG_NO_MIRROR_SELECT=1 pkg install -y python proot-distro curl >> "$HOME/hrt-setup-packages.log" 2>&1
fi
python - <<'HRT_UNPACK'
import base64, json, os
from pathlib import Path
bundle = json.loads(base64.b64decode(os.environ['HRT_SETUP_BUNDLE']))
root = Path.home() / '.local/share/hormonelog-chat'
root.mkdir(parents=True, exist_ok=True, mode=0o700)
allowed = {'setup.py', 'hrt-login.py', 'hrt-chat', 'hrt-chat-guest', 'schema.json', 'prompt.txt'}
assert set(bundle['files']) == allowed
for name, data in bundle['files'].items():
    target = root / name
    tmp = root / (name + '.tmp')
    tmp.write_bytes(base64.b64decode(data))
    tmp.chmod(0o700)
    tmp.replace(target)
(root / 'bundle-id').write_text(bundle['id'])
HRT_UNPACK
exec python "$HOME/.local/share/hormonelog-chat/setup.py" install
