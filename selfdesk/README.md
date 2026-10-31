# SelfDesk: your own remote desktop (like TeamViewer)

Control your office PC from home, and every PC in your house, from any browser.
No subscription, no third-party company in the middle: it runs on **your** server.

```
  Office PC ─┐                              ┌─ your browser (laptop / phone)
  Home PC 1 ─┼── agent ──► SelfDesk server ◄─┤
  Home PC 2 ─┘   (outbound only)  (relay)    └─ another browser
```

* **Agent** (`agent/`) runs on each PC. It connects *out* to the server, so you
  never need port forwarding or a static IP at home or in the office.
* **Server** (`server/`) relays the screen and your mouse/keyboard, and serves
  the web viewer.
* **Viewer** is just a web page: open `https://your-server/` on any computer or phone.

## Features

* List of online PCs with name, OS and 9-digit Device ID
* Live screen, mouse (left/right/middle, wheel), keyboard and shortcuts (Ctrl+C, Alt+Tab…)
* Works with any local keyboard layout and Caps Lock
* Multiple monitors, quality presets (Fast / Balanced / Sharp), full screen
* "Type text" button to paste text onto the remote PC
* View-only mode, Task Manager button
* Touch support (tap to click) so you can use it from a phone
* Only changed parts of the screen are sent, with flow control for slow connections

## Security

Three layers protect your PCs:

1. **Enroll token**: only PCs that know it can join your server.
2. **Access key**: needed in the browser to see the PC list or connect.
3. **Device password** per PC: checked *by the PC itself*. The browser proves it
   knows the password with a challenge-response (HMAC-SHA256), so the password is
   never sent over the network and the server never knows it. After 5 wrong
   passwords the PC refuses logins for 5 minutes.

Always run the server behind **HTTPS** (the Docker setup below does this
automatically) so the screen and keystrokes are encrypted on the internet.

---

## Step 1: Set up the server (once)

You need one always-on machine reachable from the internet.

### Option A (recommended): a small cloud VPS with Docker

Any 1 GB VPS works (DigitalOcean, Hetzner, AWS Lightsail, Oracle free tier…).
Point a domain such as `desk.yourcompany.com` to its IP, then on the VPS:

```bash
git clone https://github.com/faisalkazi123-code/Self-Wallet.git
cd Self-Wallet/selfdesk
cp .env.example .env
nano .env            # set domain, enroll token and access key
docker compose up -d
```

Caddy gets a free HTTPS certificate automatically. Open `https://desk.yourcompany.com`.

Generate strong secrets with:
```bash
python3 -c "import secrets;print(secrets.token_urlsafe(24))"
```

### Option B: one of your home PCs is the server

```bash
cd selfdesk/server
pip install -r requirements.txt
export SELFDESK_ENROLL_TOKEN=...   # on Windows: set SELFDESK_ENROLL_TOKEN=...
export SELFDESK_ACCESS_KEY=...
python server.py                   # listens on port 8080
```

To reach it from the office, forward a port on your home router to this PC and use
a dynamic-DNS name (e.g. DuckDNS). Put HTTPS in front of it (Caddy:
`caddy reverse-proxy --from yourname.duckdns.org --to localhost:8080`).
Inside the house only, `http://<server-pc-ip>:8080` is enough.

## Step 2: Install the agent on every PC

Copy the `agent/` folder to the PC.

**Windows (easiest): one file, `SelfDeskAgent.exe`**

1. Download `SelfDeskAgent.exe` from this repository's
   [Releases](https://github.com/faisalkazi123-code/Self-Wallet/releases) page (look for
   *SelfDesk Agent for Windows*), or from the latest *SelfDesk Windows agent* run under
   **Actions** → Artifacts.
2. Double-click it. If SmartScreen says "Windows protected your PC", click
   *More info* → *Run anyway* (the file isn't code-signed).
3. Enter server URL, enroll token, a name and a password → **Install and start**.
4. The window shows the **Device ID** and a green *Online* status. Done.

No Python and no admin rights needed. It installs to `%LOCALAPPDATA%\SelfDesk`, starts
with Windows, appears in the Start menu as *SelfDesk* (Device ID, status, change password,
uninstall) and in *Settings → Apps* for uninstalling. Double-clicking a newer
`SelfDeskAgent.exe` offers an **Update** button and keeps the same Device ID.

*For a company:* put a `selfdesk-defaults.json` next to the exe so employees only type
a name and a password:
```json
{"server": "https://desk.mycompany.com", "token": "YOUR_ENROLL_TOKEN", "lock": true}
```
or install silently (e.g. via a login script):
```
SelfDeskAgent.exe --install --server https://desk.mycompany.com --token TOKEN --name "%COMPUTERNAME%" --password PASS
SelfDeskAgent.exe --uninstall --quiet
```
Build it yourself on Windows with `agent\build-exe.bat`.

**Windows with Python** (alternative): install Python 3.10+, then double-click
`install-windows.bat`.

**Linux** (X11 desktop): `./install-linux.sh`

**macOS / manual**:
```bash
pip install -r requirements.txt
python agent.py --server https://desk.yourcompany.com --token ENROLL_TOKEN --name "Office PC"
```
On macOS allow *Screen Recording* and *Accessibility* for Python/Terminal in
System Settings → Privacy & Security.

The agent prints the **Device ID**. Change the password later with
`python agent.py --set-password`.


## Step 3: Connect

1. Open your server URL in a browser, enter the access key.
2. Click a PC in the list (or type its Device ID), enter its password, **Connect**.
3. Click inside the screen to control it. **Disconnect** when done.

## Limitations (honest list)

* The agent runs as the logged-in user. It can't show the Windows login screen,
  the lock screen (after Win+L) or UAC prompts, which Windows keeps on a protected
  desktop. Keep the PC logged in (lock with a screensaver without password, or set
  up auto-login) if you need to reach it unattended. A Windows service version would
  be the next step for this.
* Linux needs an X11 session (Wayland blocks screen capture/input from apps).
* No file transfer, audio or clipboard sync yet (use "Type text" for text).
* Video is JPEG-based: fine for office work, not for games or watching videos.

If you need those features today, [RustDesk](https://rustdesk.com) is a mature
open-source, self-hostable alternative.

---

## हिंदी में जल्दी समझें

**यह क्या है:** TeamViewer जैसा अपना खुद का सॉफ़्टवेयर। ऑफिस से घर का PC, और घर के बाकी PC, ब्राउज़र से चला सकते हैं। सब कुछ आपके अपने सर्वर पर चलता है।

**1. सर्वर (एक बार):** एक छोटा क्लाउड VPS लें, उस पर डोमेन लगाएँ, फिर `selfdesk` फ़ोल्डर में `.env` भरकर `docker compose up -d` चलाएँ। HTTPS अपने आप लग जाएगा।

**2. हर PC पर एजेंट:** Windows पर GitHub के Releases पेज से `SelfDeskAgent.exe` डाउनलोड करें और डबल-क्लिक करें (Python की ज़रूरत नहीं)। अगर "Windows protected your PC" आए तो *More info* → *Run anyway* दबाएँ। सर्वर URL, enroll token, PC का नाम और पासवर्ड डालें, **Install and start** दबाएँ। विंडो में Device ID और हरा *Online* दिखेगा। यह हर लॉगिन पर अपने आप चलेगा। हटाने के लिए Settings → Apps या Start menu में *SelfDesk* खोलें।

**3. कनेक्ट करें:** किसी भी ब्राउज़र (मोबाइल भी) में अपना सर्वर URL खोलें, access key डालें, PC चुनें, उसका पासवर्ड डालें और **Connect** दबाएँ।

**सुरक्षा:** तीन ताले हैं: enroll token (कौन-सा PC जुड़ सकता है), access key (कौन देख सकता है), और हर PC का अपना पासवर्ड (जो नेटवर्क पर कभी नहीं जाता)। 5 गलत पासवर्ड पर PC 5 मिनट के लिए लॉक हो जाता है।

**ध्यान दें:** PC लॉग-इन होना चाहिए। Windows का लॉगिन/लॉक स्क्रीन रिमोट से नहीं दिखेगा।
