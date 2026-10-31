"""End-to-end check: acts like the browser viewer against a real server + agent.

  python e2e_viewer.py --url http://127.0.0.1:8080 --key ACCESS_KEY --id DEVICE_ID --password PASS

Checks: device appears online, wrong password is refused, right password is
accepted, a real screen frame arrives and decodes, and (on Windows) the mouse
really moves to where the viewer asked.
"""
import argparse
import asyncio
import hashlib
import hmac
import io
import json
import struct
import sys
import time

import aiohttp
from PIL import Image


def proof(device_id, password, nonce):
    k = hashlib.sha256(f"selfdesk:{device_id}:{password}".encode()).digest()
    return hmac.new(k, nonce.encode(), hashlib.sha256).hexdigest()


async def login(http, ws_url, a, password):
    ws = await http.ws_connect(ws_url)
    m = json.loads((await ws.receive(timeout=15)).data)
    assert m["type"] == "challenge", m
    await ws.send_str(json.dumps({"type": "auth", "proof": proof(a.id, password, m["nonce"])}))
    m = json.loads((await ws.receive(timeout=15)).data)
    return ws, m


async def main(a):
    base = a.url.rstrip("/")
    async with aiohttp.ClientSession() as http:
        deadline = time.time() + a.wait
        while True:
            async with http.get(base + "/api/devices", headers={"X-Access-Key": a.key}) as r:
                devices = await r.json() if r.status == 200 else []
            if any(d["id"] == a.id for d in devices):
                break
            if time.time() > deadline:
                sys.exit(f"FAIL device {a.id} never came online: {devices}")
            await asyncio.sleep(1)
        print("ok  device online:", [d for d in devices if d["id"] == a.id][0])

        ws_url = base.replace("http", "ws", 1) + f"/ws/viewer?id={a.id}&key={a.key}"
        ws, m = await login(http, ws_url, a, "definitely-wrong")
        assert m["type"] == "auth_fail", m
        await ws.close()
        print("ok  wrong password refused:", m["reason"])

        ws, m = await login(http, ws_url, a, a.password)
        assert m["type"] == "auth_ok", m
        print("ok  logged in, monitors:", m["monitors"])
        await ws.send_str(json.dumps({"type": "settings", "quality": 60, "max_width": 1600,
                                      "fps": 10, "monitor": 1}))
        while True:
            msg = await ws.receive(timeout=20)
            if msg.type == aiohttp.WSMsgType.BINARY:
                break
        w, h, x, y = struct.unpack(">HHHH", msg.data[:8])
        img = Image.open(io.BytesIO(msg.data[8:]))
        img.load()
        assert w > 100 and h > 100 and img.size[0] > 0, (w, h, img.size)
        img.save(a.screenshot)
        print(f"ok  screen frame {w}x{h}, patch {img.size} at {x},{y} -> {a.screenshot}")

        mon = m["monitors"][0]
        await ws.send_str(json.dumps({"type": "mouse", "action": "move", "x": 0.25, "y": 0.75}))
        await asyncio.sleep(1)
        if sys.platform == "win32":
            import ctypes
            import ctypes.wintypes
            ctypes.windll.user32.SetProcessDPIAware()
            pt = ctypes.wintypes.POINT()
            ctypes.windll.user32.GetCursorPos(ctypes.byref(pt))
            want = (round(0.25 * (mon["width"] - 1)), round(0.75 * (mon["height"] - 1)))
            print("    cursor at", (pt.x, pt.y), "expected about", want)
            assert abs(pt.x - want[0]) <= 3 and abs(pt.y - want[1]) <= 3, "mouse did not move"
            print("ok  mouse moved on the real Windows desktop")
        await ws.close()
    print("ALL CHECKS PASSED")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--key", required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--password", required=True)
    ap.add_argument("--wait", type=int, default=60)
    ap.add_argument("--screenshot", default="frame.jpg")
    asyncio.run(main(ap.parse_args()))
