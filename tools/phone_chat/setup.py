"""Fixed installer and sanitized health checks, running inside Termux only."""
import base64
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlparse

BASE = Path.home() / '.local/share/hormonelog-chat'
PREFIX = Path(os.environ.get('PREFIX', '/data/data/com.termux/files/usr'))
VERSION = '2.1.292'
INSTALLER_SHA = '3a68d3406cf674e17bed1733a4dcf37805e2e47d87417700007d7e1aa766a944'
CONTAINERS = ('ubuntu', 'hormonelog-ubuntu24')
ENV = '/home/hrt/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin'

def run(args, data=None, timeout=120):
    return subprocess.run(args, input=data, capture_output=True, timeout=timeout)

def guest(container, args, user='hrt', data=None, timeout=120):
    return run(['proot-distro', 'login', '--isolated', '--user', user, '--work-dir', '/' if user == 'root' else '/home/hrt/work',
                container, '--', '/usr/bin/env', '-u', 'ANTHROPIC_API_KEY', '-u', 'ANTHROPIC_AUTH_TOKEN',
                '-u', 'CLAUDE_CODE_OAUTH_TOKEN', 'PATH=' + ENV, 'DISABLE_AUTOUPDATER=1'] + args, data, timeout)

def ubuntu(container):
    r = guest(container, ['/bin/sh', '-c', '. /etc/os-release; printf %s "$ID:$VERSION_ID"'], 'root')
    return '24.04' if r.returncode == 0 and r.stdout.strip() == b'ubuntu:24.04' else ''

def container():
    saved = BASE / 'container'
    if saved.exists() and saved.read_text().strip() in CONTAINERS:
        return saved.read_text().strip()
    if ubuntu('ubuntu'):
        r = guest('ubuntu', ['/home/hrt/.local/bin/claude', '--version'])
        if r.returncode == 0:
            return 'ubuntu'
    return 'hormonelog-ubuntu24'

def progress(phase, error=''):
    phase = phase if phase in {'termux', 'ubuntu', 'claude', 'bridge', 'installed', 'login', 'ready', 'error'} else 'error'
    errors = {'', 'ARCHITECTURE', 'UBUNTU_DOWNLOAD', 'UBUNTU', 'CLAUDE', 'BRIDGE', 'CLAUDE_VERSION',
              'LOGIN', 'CODE', 'LOGIN_NOT_RUNNING', 'SUBSCRIPTION_REQUIRED', 'USAGE_LIMIT', 'CONNECTION', 'MODE', 'EXECUTION'}
    error = error if error in errors else 'EXECUTION'
    BASE.mkdir(parents=True, exist_ok=True, mode=0o700)
    tmp = BASE / 'progress.tmp'
    tmp.write_text(json.dumps({'phase': phase, 'error': error}))
    tmp.replace(BASE / 'progress.json')
    # History contains fixed stage/error codes only, never CLI output or OAuth material.
    fd = os.open(BASE / 'events.jsonl', os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    with os.fdopen(fd, 'a') as events:
        fcntl.flock(events, fcntl.LOCK_EX)
        events.write(json.dumps({'at': datetime.now(timezone.utc).isoformat(), 'phase': phase, 'error': error}) + '\n')

def official_url(value):
    try:
        p = urlparse(value)
        route = (p.hostname == 'claude.com' and p.path == '/cai/oauth/authorize') or (p.hostname in {'claude.ai', 'platform.claude.com', 'console.anthropic.com'} and p.path == '/oauth/authorize')
        return value if p.scheme == 'https' and route and not p.username and not p.password and p.port is None and p.query else ''
    except ValueError:
        return ''

def register_runtime(c, path=None):
    if c not in CONTAINERS:
        raise ValueError('unsupported runtime container')
    target = path or Path.home() / '.local/share/claude-phone/runtime.json'
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    data = {'schemaVersion': 1, 'transport': 'termux-run-command', 'container': c,
            'user': 'hrt', 'home': '/home/hrt', 'executable': '/home/hrt/.local/bin/claude',
            'ubuntuVersion': '24.04', 'claudeVersion': VERSION}
    tmp = target.with_suffix('.tmp')
    tmp.write_text(json.dumps(data))
    tmp.chmod(0o600)
    tmp.replace(target)

def probe():
    result = {'ubuntu': '', 'claude': '', 'bridgeId': '', 'loggedIn': False, 'authMethod': '', 'loginUrl': '', 'phase': '', 'error': '', 'running': False}
    if (BASE / 'job-pid').exists():
        try:
            pid = int((BASE / 'job-pid').read_text())
            os.kill(pid, 0)
            result['running'] = True
        except (ValueError, ProcessLookupError):
            pass
    if (BASE / 'progress.json').exists():
        p = json.loads((BASE / 'progress.json').read_text())
        result.update({k: p.get(k, '') for k in ('phase', 'error')})
    c = container()
    result['ubuntu'] = ubuntu(c)
    if not result['ubuntu']:
        return result
    version = guest(c, ['/home/hrt/.local/bin/claude', '--version'], timeout=25)
    m = re.match(rb'(\d+\.\d+\.\d+)\b', version.stdout)
    result['claude'] = m.group(1).decode() if version.returncode == 0 and m else ''
    if result['claude']:
        auth = guest(c, ['/home/hrt/.local/bin/claude', 'auth', 'status'], timeout=25)
        try:
            a = json.loads(auth.stdout)
            result.update(loggedIn=a.get('loggedIn') is True, authMethod=a.get('authMethod', ''))
        except (ValueError, TypeError):
            pass
    if (PREFIX / 'bin/hrt-chat').is_file() and (BASE / 'installed-id').is_file():
        paths = {'hrt-chat-guest': '/home/hrt/.local/bin/hrt-chat-guest', 'schema.json': '/home/hrt/chat/schema.json',
                 'prompt.txt': '/home/hrt/chat/prompt.txt', 'hrt-login.py': '/home/hrt/chat/hrt-login.py'}
        script = 'import hashlib,json;from pathlib import Path;print(json.dumps({k:hashlib.sha256(Path(v).read_bytes()).hexdigest() for k,v in ' + repr(paths) + '.items()}))'
        hashes = guest(c, ['/usr/bin/python3', '-c', script], timeout=15)
        try:
            actual = json.loads(hashes.stdout)
            good = hashes.returncode == 0 and all(actual.get(n) == hashlib.sha256((BASE / n).read_bytes()).hexdigest() for n in paths)
            good = good and (PREFIX / 'bin/hrt-chat').read_bytes() == (BASE / 'hrt-chat').read_bytes()
            if good:
                result['bridgeId'] = (BASE / 'installed-id').read_text().strip()
        except (ValueError, OSError):
            pass
    if not result['loggedIn']:
        url = guest(c, ['/bin/cat', '/home/hrt/chat/login-url'], timeout=10)
        result['loginUrl'] = official_url(url.stdout.decode(errors='replace').strip())
    return result

def checked(args, stage, user='root', data=None, timeout=900):
    progress(stage)
    r = guest(container(), args, user, data, timeout)
    with (BASE / 'install.log').open('ab') as log:
        log.write(r.stdout); log.write(r.stderr)
    if r.returncode:
        raise RuntimeError(stage.upper())
    return r

def cancel_login():
    # Only the PID recorded by our helper, with its expected command line, is stopped.
    script = '''import os,signal
from pathlib import Path
p=Path('/home/hrt/chat/login-pid')
if p.exists():
 try:
  pid=int(p.read_text());cmd=Path(f'/proc/{pid}/cmdline').read_bytes()
  if b'/home/hrt/chat/hrt-login.py' in cmd and b'login' in cmd:
   for child in Path(f'/proc/{pid}/task/{pid}/children').read_text().split():
    c=int(child);args=Path(f'/proc/{c}/cmdline').read_bytes()
    if b'claude' in args and b'auth' in args and b'login' in args:os.kill(c,signal.SIGTERM)
   os.kill(pid,signal.SIGTERM)
 except (ValueError,ProcessLookupError,FileNotFoundError):pass
'''
    guest(container(), ['/usr/bin/python3', '-c', script], timeout=15)

def install():
    BASE.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (BASE / 'install.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return probe()
        (BASE / 'job-pid').write_text(str(os.getpid()))
        c = container()
        (BASE / 'container').write_text(c)
        if not ubuntu(c):
            progress('ubuntu')
            arch = {'aarch64': 'arm64', 'x86_64': 'amd64'}.get(os.uname().machine)
            hashes = {'arm64': 'a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2',
                      'amd64': 'e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86'}
            if arch not in hashes:
                raise RuntimeError('ARCHITECTURE')
            archive = BASE / ('ubuntu-base-24.04.5-base-' + arch + '.tar.gz')
            if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != hashes[arch]:
                url = 'https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/' + archive.name
                r = run(['curl', '--proto', '=https', '--tlsv1.2', '-fsSL', '--retry', '3', '--max-time', '600', '-o', str(archive), url], timeout=660)
                if r.returncode or hashlib.sha256(archive.read_bytes()).hexdigest() != hashes[arch]:
                    raise RuntimeError('UBUNTU_DOWNLOAD')
            # A dedicated name avoids resetting an unrelated existing Ubuntu installation.
            r = run(['proot-distro', 'install', str(archive), '--name', c], timeout=1200)
            with (BASE / 'install.log').open('ab') as log:
                log.write(r.stdout); log.write(r.stderr)
            if r.returncode or not ubuntu(c):
                raise RuntimeError('UBUNTU')
        checked(['/bin/bash', '-c', '''set -eu
if ! command -v curl >/dev/null || ! command -v python3 >/dev/null || ! command -v useradd >/dev/null || [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl python3 passwd
fi
id hrt >/dev/null 2>&1 || useradd --create-home --shell /bin/bash hrt
mkdir -p /home/hrt/work /home/hrt/chat /home/hrt/.local/bin
chown hrt:hrt /home/hrt/work /home/hrt/chat /home/hrt/.local /home/hrt/.local/bin
chmod 700 /home/hrt/work /home/hrt/chat
'''], 'ubuntu')
        version = guest(c, ['/home/hrt/.local/bin/claude', '--version'], timeout=25)
        if version.returncode or not version.stdout.startswith((VERSION + ' ').encode()):
            checked(['/bin/bash', '-c', '''set -eu
curl --proto '=https' --tlsv1.2 -fsSL https://claude.ai/install.sh -o /home/hrt/chat/install.sh
printf '%s  %s\n' "''' + INSTALLER_SHA + '''" /home/hrt/chat/install.sh | sha256sum -c -
bash /home/hrt/chat/install.sh ''' + VERSION], 'claude', user='hrt', timeout=900)
        progress('bridge')
        cancel_login()
        files = {n: base64.b64encode((BASE / n).read_bytes()).decode() for n in ('hrt-chat-guest', 'schema.json', 'prompt.txt', 'hrt-login.py')}
        deploy = '''import base64,json,os,pwd,sys
from pathlib import Path
u=pwd.getpwnam('hrt')
for name,value in json.load(sys.stdin).items():
 assert name in ('hrt-chat-guest','schema.json','prompt.txt','hrt-login.py')
 p=Path('/home/hrt/.local/bin/hrt-chat-guest') if name=='hrt-chat-guest' else Path('/home/hrt/chat')/name
 t=p.with_suffix(p.suffix+'.tmp');t.write_bytes(base64.b64decode(value));t.chmod(0o700);os.chown(t,u.pw_uid,u.pw_gid);t.replace(p)
'''
        checked(['/usr/bin/python3', '-c', deploy], 'bridge', data=json.dumps(files).encode())
        target = PREFIX / 'bin/hrt-chat'
        if target.exists() and not (BASE / 'hrt-chat.before-setup').exists():
            (BASE / 'hrt-chat.before-setup').write_bytes(target.read_bytes())
        tmp = target.with_suffix('.tmp')
        tmp.write_bytes((BASE / 'hrt-chat').read_bytes()); tmp.chmod(0o700); tmp.replace(target)
        r = guest(c, ['/home/hrt/.local/bin/claude', '--version'], timeout=25)
        if r.returncode or not r.stdout.startswith((VERSION + ' ').encode()):
            raise RuntimeError('CLAUDE_VERSION')
        register_runtime(c)
        (BASE / 'installed-id').write_text((BASE / 'bundle-id').read_text())
        progress('installed')
        return probe()

def main():
    mode = sys.argv[1] if len(sys.argv) == 2 else ''
    if mode == 'probe':
        return probe()
    if mode == 'install':
        return install()
    if mode == 'login':
        progress('login')
        r = guest(container(), ['/usr/bin/python3', '/home/hrt/chat/hrt-login.py', 'login'], timeout=660)
        if r.returncode:
            progress('error', 'LOGIN')
        return probe()
    if mode == 'code':
        value = base64.b64decode(os.environ.get('HRT_SETUP_CODE', '')).decode()
        if not 1 <= len(value) <= 4096 or any(c in value for c in '\r\n\x00'):
            raise RuntimeError('CODE')
        r = guest(container(), ['/usr/bin/python3', '/home/hrt/chat/hrt-login.py', 'code'], data=value.encode(), timeout=10)
        if r.returncode:
            raise RuntimeError('LOGIN_NOT_RUNNING')
        return {'accepted': True}
    if mode == 'cancel_login':
        cancel_login()
        return {'cancelled': True}
    if mode == 'verify':
        report = probe()
        if report['claude'] != VERSION or report['authMethod'] != 'claude.ai' or not report['loggedIn']:
            raise RuntimeError('SUBSCRIPTION_REQUIRED')
        payload = json.dumps({'messages': [{'role': 'user', 'text': '최근 기록 보여줘'}], 'memoryNames': [], 'previousDoseTimeAvailable': False}).encode()
        r = run([str(PREFIX / 'bin/hrt-chat'), 'claude-haiku-5-5'], data=payload, timeout=95)
        o = json.loads(r.stdout)
        if r.returncode or o.get('is_error') or o.get('structured_output', {}).get('action') != 'recent':
            if 'limit' in str(o.get('result', '')).lower():
                raise RuntimeError('USAGE_LIMIT')
            raise RuntimeError('CONNECTION')
        progress('ready')
        return {'verified': True}
    raise RuntimeError('MODE')

if __name__ == '__main__':
    os.umask(0o077)
    try:
        print(json.dumps(main()), flush=True)
    except Exception as e:
        code = str(e) if isinstance(e, RuntimeError) else 'EXECUTION'
        progress('error', code)
        print(json.dumps({'phase': 'error', 'error': code}), flush=True)
    finally:
        p = BASE / 'job-pid'
        if p.exists() and p.read_text().strip() == str(os.getpid()):
            p.unlink()
