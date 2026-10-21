"""Provision the reviewed chat launcher into an already configured debug Termux app."""
import argparse
import base64
from pathlib import Path
import shlex
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--enable-app-commands', action='store_true')
args = p.parse_args()
adb = [args.adb, '-s', args.serial]
prefix = '/data/data/com.termux/files/usr'
home = '/data/data/com.termux/files/home'
env = ['run-as', 'com.termux', '/system/bin/env', 'HOME=' + home, 'PREFIX=' + prefix,
       'TMPDIR=' + prefix + '/tmp', 'PATH=' + prefix + '/bin:/system/bin']

def run(script):
    remote = shlex.join(env + [prefix + '/bin/bash', '-c', script])
    subprocess.run(adb + ['shell', remote], check=True)

for name in ('schema.json', 'prompt.txt', 'hrt-chat-guest', 'hrt-chat'):
    data = (Path(__file__).parent / name).read_bytes().replace(b'\r\n', b'\n')
    encoded = base64.b64encode(data).decode('ascii')
    if name == 'hrt-chat':
        dest = prefix + '/bin/hrt-chat'
        script = f"printf %s {shlex.quote(encoded)} | base64 -d > {shlex.quote(dest)}\nchmod 700 {shlex.quote(dest)}"
    else:
        dest = '/home/hrt/.local/bin/hrt-chat-guest' if name == 'hrt-chat-guest' else '/home/hrt/chat/' + name
        guest = f"mkdir -p /home/hrt/chat /home/hrt/.local/bin\nprintf %s {shlex.quote(encoded)} | base64 -d > {shlex.quote(dest)}\nchmod 700 {shlex.quote(dest)}"
        script = shlex.join(['proot-distro', 'login', '--isolated', '--user', 'hrt', 'ubuntu', '--', '/bin/bash', '-c', guest])
    run(script)
if args.enable_app_commands:
    script = """import pathlib, re
p=pathlib.Path('/data/data/com.termux/files/home/.termux/termux.properties')
p.parent.mkdir(parents=True,exist_ok=True)
s=p.read_text() if p.exists() else ''
backup=p.with_name('termux.properties.before-hrt-chat')
if p.exists() and not backup.exists(): backup.write_text(s)
s=re.sub(r'^\\s*allow-external-apps\\s*=.*$', '', s, flags=re.M)
p.write_text(s.rstrip()+'\\nallow-external-apps=true\\n')
"""
    run(shlex.join([prefix + '/bin/python', '-c', script]))
    run('termux-reload-settings')
print('Fixed chat launcher installed; no Claude credential files were read or copied.')
