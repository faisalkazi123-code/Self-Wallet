"""SelfDesk agent: run this on every PC you want to reach remotely.

First run (sets everything up and saves it to config.json next to this file):
    python agent.py --server https://desk.example.com --token ENROLL_TOKEN --name "Home PC"

Later runs just need:
    python agent.py

Change the password any time:
    python agent.py --set-password
"""
import argparse
import asyncio
import getpass
import hashlib
import hmac
import io
import json
import logging
import platform
import secrets
import socket
import struct
import sys
import time
from pathlib import Path
from urllib.parse import urlencode

import aiohttp
import mss
from PIL import Image, ImageChops

from inputs import InputController

log = logging.getLogger("selfdesk.agent")
def screens():
    return (getattr(mss, "MSS", None) or mss.mss)()


CONFIG = Path(getattr(sys, "frozen", False) and sys.executable or __file__).with_name("config.json")
MAX_FAILS, LOCK_SECONDS = 5, 300


def derive_key(device_id: str, password: str) -> str:
    # The viewer derives the same key in the browser; only this hash is stored.
    return hashlib.sha256(f"selfdesk:{device_id}:{password}".encode()).hexdigest()


def load_config():
    if CONFIG.exists():
        return json.loads(CONFIG.read_text())
    return {}


def save_config(cfg):
    CONFIG.write_text(json.dumps(cfg, indent=2))
    try:
        CONFIG.chmod(0o600)
    except OSError:
        pass


def ask_password(device_id):
    while True:
        p1 = getpass.getpass("Choose a password for this PC (8+ characters): ")
        if len(p1) < 8:
            print("Too short.")
            continue
        if p1 != getpass.getpass("Repeat password: "):
            print("Passwords do not match.")
            continue
        return derive_key(device_id, p1)


class Session:
    def __init__(self, sid, peer):
        self.sid = sid
        self.peer = peer
        self.nonce = secrets.token_hex(16)
        self.authed = False
        self.task = None
        self.ack = asyncio.Event()
        self.ack.set()
        self.quality = 60
        self.max_width = 1600
        self.fps = 12
        self.monitor = 1
        self.force_full = True
        self.view_only = False


class Agent:
    def __init__(self, cfg, on_status=None):
        self.cfg = cfg
        self.on_status = on_status or (lambda state, detail="": None)
        self.sessions = {}
        self.fails = []
        self.ws = None
        self.input = InputController()
        self.send_lock = asyncio.Lock()

    # ----- networking -----
    def url(self):
        base = self.cfg["server"].rstrip("/")
        base = base.replace("https://", "wss://", 1).replace("http://", "ws://", 1)
        info = f"{platform.system()} {platform.release()} - {socket.gethostname()}"
        return f"{base}/ws/agent?" + urlencode({
            "id": self.cfg["id"], "token": self.cfg["token"],
            "name": self.cfg.get("name") or socket.gethostname(), "info": info})

    async def send_json(self, obj):
        async with self.send_lock:
            await self.ws.send_str(json.dumps(obj))

    async def send_bytes(self, data):
        async with self.send_lock:
            await self.ws.send_bytes(data)

    async def run_forever(self):
        delay = 2
        while True:
            try:
                async with aiohttp.ClientSession() as http:
                    async with http.ws_connect(self.url(), heartbeat=20,
                                               max_msg_size=4 * 1024 * 1024) as ws:
                        self.ws = ws
                        delay = 2
                        self.on_status("online")
                        log.info("connected to %s as device ID %s", self.cfg["server"], self.cfg["id"])
                        async for msg in ws:
                            if msg.type == aiohttp.WSMsgType.TEXT:
                                await self.handle(json.loads(msg.data))
            except aiohttp.WSServerHandshakeError as e:
                log.error("server refused connection: %s %s", e.status, e.message)
                self.on_status("error", "wrong enroll token" if e.status == 401 else f"server said {e.status}")
                if e.status == 401:
                    log.error("check the enroll token in %s", CONFIG)
            except (aiohttp.ClientError, OSError) as e:
                log.warning("connection problem: %s", e)
                self.on_status("offline", str(e)[:200])
            finally:
                for s in list(self.sessions.values()):
                    self.close_session(s.sid)
            log.info("reconnecting in %ss", delay)
            await asyncio.sleep(delay)
            delay = min(delay * 2, 60)

    # ----- protocol -----
    def locked(self):
        now = time.time()
        self.fails = [t for t in self.fails if now - t < LOCK_SECONDS]
        return len(self.fails) >= MAX_FAILS

    async def handle(self, m):
        t, sid = m.get("type"), m.get("sid")
        if t == "session_open":
            s = Session(sid, m.get("from"))
            self.sessions[sid] = s
            await self.send_json({"type": "challenge", "sid": sid, "nonce": s.nonce,
                                  "name": self.cfg.get("name", "")})
            return
        s = self.sessions.get(sid)
        if s is None:
            return
        if t == "session_close":
            self.close_session(sid)
        elif t == "auth":
            await self.auth(s, m)
        elif not s.authed:
            return  # nothing else is allowed before the password check
        elif t == "ack":
            s.ack.set()
        elif t == "settings":
            s.quality = max(10, min(95, int(m.get("quality", s.quality))))
            s.max_width = max(640, min(3840, int(m.get("max_width", s.max_width))))
            s.fps = max(1, min(30, int(m.get("fps", s.fps))))
            mon = int(m.get("monitor", s.monitor))
            with screens() as sct:
                if 1 <= mon < len(sct.monitors):
                    s.monitor = mon
            s.view_only = bool(m.get("view_only", s.view_only))
            s.force_full = True
        elif t == "refresh":
            s.force_full = True
        elif not s.view_only:
            mon = self.monitor_rect(s.monitor)
            try:
                self.input.handle(m, mon)
            except Exception as e:  # never let a bad key kill the agent
                log.debug("input error: %s", e)

    async def auth(self, s, m):
        if s.authed:
            return
        if self.locked():
            await self.send_json({"type": "auth_fail", "sid": s.sid,
                                  "reason": "too many wrong passwords, try again in 5 minutes"})
            return
        expected = hmac.new(bytes.fromhex(self.cfg["key"]), s.nonce.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, str(m.get("proof", ""))):
            self.fails.append(time.time())
            log.warning("wrong password from %s", s.peer)
            await self.send_json({"type": "auth_fail", "sid": s.sid, "reason": "wrong password"})
            self.sessions.pop(s.sid, None)
            return
        s.authed = True
        log.info("viewer connected from %s", s.peer)
        with screens() as sct:
            monitors = [{"index": i, "width": mo["width"], "height": mo["height"]}
                        for i, mo in enumerate(sct.monitors) if i > 0]
        await self.send_json({"type": "auth_ok", "sid": s.sid, "monitors": monitors,
                              "name": self.cfg.get("name", "")})
        s.task = asyncio.create_task(self.stream(s))

    def close_session(self, sid):
        s = self.sessions.pop(sid, None)
        if s and s.task:
            s.task.cancel()
            log.info("viewer disconnected")
        if not any(x.authed for x in self.sessions.values()):
            self.input.release_all()

    def monitor_rect(self, index):
        with screens() as sct:
            mons = sct.monitors
            return mons[index] if index < len(mons) else mons[1]

    # ----- screen streaming -----
    async def stream(self, s):
        loop = asyncio.get_running_loop()
        prev = None
        sct = screens()
        try:
            while True:
                start = time.monotonic()
                try:
                    await asyncio.wait_for(s.ack.wait(), timeout=3)
                except asyncio.TimeoutError:
                    pass  # viewer is slow or lost an ack, carry on anyway
                packet, prev = await loop.run_in_executor(None, self.grab, sct, s, prev)
                if packet:
                    s.ack.clear()
                    await self.send_bytes(s.sid.encode() + packet)
                await asyncio.sleep(max(0, 1 / s.fps - (time.monotonic() - start)))
        except asyncio.CancelledError:
            pass
        except Exception:
            log.exception("stream stopped")
        finally:
            sct.close()

    @staticmethod
    def grab(sct, s, prev):
        mon = sct.monitors[s.monitor] if s.monitor < len(sct.monitors) else sct.monitors[1]
        shot = sct.grab(mon)
        img = Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")
        if img.width > s.max_width:
            img = img.resize((s.max_width, round(img.height * s.max_width / img.width)),
                             Image.BILINEAR)
        if prev is None or s.force_full or prev.size != img.size:
            box = (0, 0, img.width, img.height)
            s.force_full = False
        else:
            box = ImageChops.difference(img, prev).getbbox()
            if box is None:
                return None, img  # nothing changed, send nothing
            # pad a little so JPEG edges blend in
            box = (max(0, box[0] - 8), max(0, box[1] - 8),
                   min(img.width, box[2] + 8), min(img.height, box[3] + 8))
        buf = io.BytesIO()
        img.crop(box).save(buf, "JPEG", quality=s.quality, optimize=False)
        # header: full width, full height, x, y of the patch (uint16 big endian)
        header = struct.pack(">HHHH", img.width, img.height, box[0], box[1])
        return header + buf.getvalue(), img


def main():
    ap = argparse.ArgumentParser(description="SelfDesk agent")
    ap.add_argument("--server", help="server URL, e.g. https://desk.example.com")
    ap.add_argument("--token", help="enroll token from the server")
    ap.add_argument("--name", help="name shown in the device list")
    ap.add_argument("--password", help="device password (asked interactively if omitted)")
    ap.add_argument("--set-password", action="store_true", help="change the device password")
    ap.add_argument("--setup-only", action="store_true", help="save settings and exit")
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    log_file = None
    if sys.stderr is None:  # started hidden (pythonw / autostart): log to a file
        log_file = CONFIG.with_name("agent.log")
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, filename=log_file,
                        format="%(asctime)s %(levelname)s %(message)s")

    cfg = load_config()
    cfg.setdefault("id", str(secrets.randbelow(900_000_000) + 100_000_000))
    for k in ("server", "token", "name"):
        if getattr(a, k):
            cfg[k] = getattr(a, k)
    if a.password:
        if len(a.password) < 8:
            raise SystemExit("password must be 8+ characters")
        cfg["key"] = derive_key(cfg["id"], a.password)
    elif a.set_password or "key" not in cfg:
        cfg["key"] = ask_password(cfg["id"])
    if not cfg.get("server") or not cfg.get("token"):
        raise SystemExit("first run needs --server and --token")
    save_config(cfg)

    print(f"\n  SelfDesk agent\n  Device ID : {cfg['id']}\n  Name      : {cfg.get('name') or socket.gethostname()}\n")
    if a.setup_only:
        return
    try:
        asyncio.run(Agent(cfg).run_forever())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
