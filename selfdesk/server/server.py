"""SelfDesk relay server.

Every PC runs the agent, which keeps ONE outbound WebSocket open to this
server, so no port forwarding is needed at home or in the office. A browser
(viewer) connects to the same server and the server just relays messages
between the viewer and the chosen agent.

The server never sees device passwords: the viewer proves it knows the
password with an HMAC over a random challenge that the agent itself checks.

Environment variables:
  SELFDESK_ENROLL_TOKEN  secret an agent must present to register (required)
  SELFDESK_ACCESS_KEY    secret a viewer must present to list/connect (required)
  SELFDESK_HOST          bind address (default 0.0.0.0)
  SELFDESK_PORT          port (default 8080)
"""
import asyncio
import hmac
import json
import logging
import os
import re
import secrets
import time
from pathlib import Path

from aiohttp import WSMsgType, web

log = logging.getLogger("selfdesk.server")
STATIC = Path(__file__).parent / "static"
ID_RE = re.compile(r"^[0-9]{6,12}$")
SID_LEN = 8  # binary frames are prefixed with the 8-char session id


def _eq(a: str, b: str) -> bool:
    return hmac.compare_digest(a.encode(), b.encode())


class Agent:
    def __init__(self, ws, device_id, name, info):
        self.ws = ws
        self.id = device_id
        self.name = name
        self.info = info
        self.since = time.time()
        self.sessions = {}  # sid -> viewer ws


class Relay:
    def __init__(self, enroll_token, access_key):
        self.enroll_token = enroll_token
        self.access_key = access_key
        self.agents = {}
        # simple per-IP throttling of bad server keys
        self.bad_keys = {}

    def _throttled(self, ip):
        t, n = self.bad_keys.get(ip, (0, 0))
        return n >= 10 and time.time() - t < 300

    def _bad(self, ip):
        t, n = self.bad_keys.get(ip, (0, 0))
        if time.time() - t > 300:
            n = 0
        self.bad_keys[ip] = (time.time(), n + 1)

    def check_key(self, request, key):
        ip = request.remote or "?"
        if self._throttled(ip):
            raise web.HTTPTooManyRequests(text="too many attempts, wait 5 minutes")
        if not key or not _eq(key, self.access_key):
            self._bad(ip)
            raise web.HTTPUnauthorized(text="wrong access key")

    # ---------- HTTP ----------
    async def index(self, request):
        return web.FileResponse(STATIC / "index.html")

    async def devices(self, request):
        self.check_key(request, request.headers.get("X-Access-Key", ""))
        return web.json_response([
            {"id": a.id, "name": a.name, "info": a.info,
             "online_since": int(a.since), "viewers": len(a.sessions)}
            for a in sorted(self.agents.values(), key=lambda a: a.name.lower())
        ])

    # ---------- agent side ----------
    async def agent_ws(self, request):
        q = request.query
        device_id = q.get("id", "")
        if not _eq(q.get("token", ""), self.enroll_token):
            raise web.HTTPUnauthorized(text="wrong enroll token")
        if not ID_RE.match(device_id):
            raise web.HTTPBadRequest(text="bad device id")
        ws = web.WebSocketResponse(heartbeat=20, max_msg_size=32 * 1024 * 1024)
        await ws.prepare(request)
        name = q.get("name", device_id)[:64]
        info = q.get("info", "")[:128]

        old = self.agents.get(device_id)
        if old:
            await self._drop_agent(old, "device reconnected")
        agent = Agent(ws, device_id, name, info)
        self.agents[device_id] = agent
        log.info("agent online: %s (%s) from %s", device_id, name, request.remote)
        try:
            async for msg in ws:
                if msg.type == WSMsgType.BINARY:
                    data = msg.data
                    sid = data[:SID_LEN].decode("ascii", "ignore")
                    v = agent.sessions.get(sid)
                    if v is not None and not v.closed:
                        await v.send_bytes(data[SID_LEN:])
                elif msg.type == WSMsgType.TEXT:
                    try:
                        m = json.loads(msg.data)
                    except ValueError:
                        continue
                    v = agent.sessions.get(m.pop("sid", None))
                    if v is not None and not v.closed:
                        await v.send_str(json.dumps(m))
                        if m.get("type") == "auth_fail":
                            await v.close()
        finally:
            if self.agents.get(device_id) is agent:
                del self.agents[device_id]
            await self._drop_agent(agent, "device went offline")
            log.info("agent offline: %s", device_id)
        return ws

    async def _drop_agent(self, agent, reason):
        for v in list(agent.sessions.values()):
            if not v.closed:
                await v.send_str(json.dumps({"type": "closed", "reason": reason}))
                await v.close()
        agent.sessions.clear()
        if not agent.ws.closed:
            await agent.ws.close()

    # ---------- viewer side ----------
    async def viewer_ws(self, request):
        q = request.query
        self.check_key(request, q.get("key", ""))
        agent = self.agents.get(q.get("id", ""))
        if agent is None:
            raise web.HTTPNotFound(text="device is offline or unknown")
        ws = web.WebSocketResponse(heartbeat=20, max_msg_size=1024 * 1024)
        await ws.prepare(request)
        sid = secrets.token_hex(SID_LEN // 2)
        agent.sessions[sid] = ws
        log.info("viewer %s -> %s (session %s)", request.remote, agent.id, sid)
        try:
            await agent.ws.send_str(json.dumps(
                {"type": "session_open", "sid": sid, "from": request.remote}))
            async for msg in ws:
                if msg.type != WSMsgType.TEXT:
                    continue
                try:
                    m = json.loads(msg.data)
                except ValueError:
                    continue
                if not isinstance(m, dict):
                    continue
                m["sid"] = sid  # viewer can never pick another session
                if agent.ws.closed:
                    break
                await agent.ws.send_str(json.dumps(m))
        finally:
            agent.sessions.pop(sid, None)
            if not agent.ws.closed:
                try:
                    await agent.ws.send_str(json.dumps({"type": "session_close", "sid": sid}))
                except ConnectionError:
                    pass
            log.info("session %s closed", sid)
        return ws


def make_app(enroll_token, access_key):
    relay = Relay(enroll_token, access_key)
    app = web.Application()
    app.add_routes([
        web.get("/", relay.index),
        web.get("/api/devices", relay.devices),
        web.get("/ws/agent", relay.agent_ws),
        web.get("/ws/viewer", relay.viewer_ws),
        web.static("/static", STATIC),
    ])
    app["relay"] = relay
    return app


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    enroll = os.environ.get("SELFDESK_ENROLL_TOKEN", "")
    access = os.environ.get("SELFDESK_ACCESS_KEY", "")
    if len(enroll) < 12 or len(access) < 12:
        raise SystemExit(
            "Set SELFDESK_ENROLL_TOKEN and SELFDESK_ACCESS_KEY (12+ characters each).\n"
            "Example: export SELFDESK_ENROLL_TOKEN=$(python3 -c 'import secrets;print(secrets.token_urlsafe(18))')")
    web.run_app(make_app(enroll, access),
                host=os.environ.get("SELFDESK_HOST", "0.0.0.0"),
                port=int(os.environ.get("SELFDESK_PORT", "8080")))


if __name__ == "__main__":
    main()
