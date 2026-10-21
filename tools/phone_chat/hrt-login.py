"""Own the official CLI's terminal. Never return its transcript or credentials."""
import fcntl
import os
from pathlib import Path
import pty
import re
import select
import signal
import struct
import subprocess
import sys
import time
import termios
from urllib.parse import urlparse

ROOT = Path('/home/hrt/chat')
URL = ROOT / 'login-url'
CODE = ROOT / 'login-code'
PID = ROOT / 'login-pid'

def official(value):
    try:
        p = urlparse(value)
        route = (p.hostname == 'claude.com' and p.path == '/cai/oauth/authorize') or (p.hostname in {'claude.ai', 'platform.claude.com', 'console.anthropic.com'} and p.path == '/oauth/authorize')
        return p.scheme == 'https' and route and not p.username and not p.password and p.port is None and bool(p.query)
    except ValueError:
        return False

def extract_url(text):
    # OSC hyperlinks may use either BEL or ESC-backslash; read the URL before stripping styling.
    return next((value for value in re.findall(r'https://[^\s\x1b\x07]+', text) if official(value)), '')

def login():
    with (ROOT / 'login-lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return 0
        for p in (URL, CODE): p.unlink(missing_ok=True)
        PID.write_text(str(os.getpid()))
        master, slave = pty.openpty()
        fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack('HHHH', 24, 8192, 0, 0))
        browser = ROOT / 'hrt-browser'
        browser.write_text('#!/bin/sh\nprintf %s "$1" > /home/hrt/chat/login-url.browser\nmv /home/hrt/chat/login-url.browser /home/hrt/chat/login-url\n')
        browser.chmod(0o700)
        env = dict(os.environ, TERM='xterm-256color', DISABLE_AUTOUPDATER='1', BROWSER=str(browser))
        process = subprocess.Popen(['/home/hrt/.local/bin/claude', 'auth', 'login', '--claudeai'],
            stdin=slave, stdout=slave, stderr=slave, env=env)
        os.close(slave)
        text = ''
        try:
            def stop(_signal, _frame):
                raise InterruptedError('cancelled')
            signal.signal(signal.SIGTERM, stop)
            deadline = time.monotonic() + 600
            while process.poll() is None and time.monotonic() < deadline:
                if select.select([master], [], [], .2)[0]:
                    try: data = os.read(master, 8192)
                    except OSError: break
                    text = (text + data.decode(errors='replace'))[-32000:]
                    value = extract_url(text)
                    if value:
                        tmp = ROOT / 'login-url.capture'
                        tmp.write_text(value); tmp.replace(URL)
                if CODE.exists():
                    value = CODE.read_text().strip()
                    CODE.unlink(missing_ok=True)
                    if value and len(value) <= 4096 and not any(c in value for c in '\r\n\x00'):
                        os.write(master, value.encode() + b'\n')
            if process.poll() is None: process.terminate()
            return process.wait(timeout=10)
        finally:
            if process.poll() is None: process.kill()
            os.close(master)
            for p in (URL, CODE, PID, CODE.with_suffix('.tmp'), ROOT / 'login-url.browser', ROOT / 'login-url.capture'): p.unlink(missing_ok=True)

if __name__ == '__main__':
    os.umask(0o077)
    mode = sys.argv[1] if len(sys.argv) == 2 else ''
    if mode == 'login':
        try: sys.exit(login())
        except InterruptedError: sys.exit(130)
    if mode == 'code':
        if not PID.exists(): sys.exit(1)
        try: os.kill(int(PID.read_text()), 0)
        except (ValueError, ProcessLookupError): sys.exit(1)
        value = sys.stdin.read(4097).strip()
        if not 1 <= len(value) <= 4096 or any(c in value for c in '\r\n\x00'): sys.exit(1)
        tmp = CODE.with_suffix('.tmp')
        tmp.write_text(value); tmp.replace(CODE)
    else:
        sys.exit(64)
