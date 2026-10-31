"""SelfDesk for Windows: one .exe that is the installer, the agent and its settings window.

Double-click SelfDeskAgent.exe:
  * not installed yet -> setup window (server, token, PC name, password) -> installs
    to %LOCALAPPDATA%\\SelfDesk, starts with Windows, shows the Device ID
  * already installed -> window with Device ID, status, change password, update, uninstall

No admin rights are needed. Command line (for IT / mass deployment):
  SelfDeskAgent.exe --install --server URL --token TOKEN --name NAME --password PASS
  SelfDeskAgent.exe --uninstall [--quiet]
  SelfDeskAgent.exe --run            (what Windows starts at login; runs hidden)

A file named selfdesk-defaults.json next to the exe pre-fills the setup window, e.g.
  {"server": "https://desk.mycompany.com", "token": "...", "lock": true}
so employees only type a PC name and a password.
"""
import argparse
import asyncio
import json
import logging
import logging.handlers
import os
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path

import agent as core

try:
    from build_info import VERSION
except ImportError:
    VERSION = "dev"

APP = "SelfDesk Agent"
EXE_NAME = "SelfDeskAgent.exe"
MUTEX = "Local\\SelfDeskAgentRunning"
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
UNINSTALL_KEY = r"Software\Microsoft\Windows\CurrentVersion\Uninstall\SelfDeskAgent"
IS_WIN = sys.platform == "win32"
FROZEN = getattr(sys, "frozen", False)
NO_WINDOW = 0x08000000 if IS_WIN else 0  # CREATE_NO_WINDOW
DETACHED = 0x00000008 | 0x00000200 if IS_WIN else 0  # DETACHED_PROCESS | NEW_PROCESS_GROUP

if IS_WIN:
    import ctypes
    import winreg

log = logging.getLogger("selfdesk.app")


# ---------------------------------------------------------------- paths
def app_dir() -> Path:
    base = os.environ.get("LOCALAPPDATA") or str(Path.home() / ".local" / "share")
    return Path(base) / "SelfDesk"


def installed_exe() -> Path:
    return app_dir() / EXE_NAME


STATUS = lambda: app_dir() / "status.json"  # noqa: E731
core.CONFIG = app_dir() / "config.json"


def is_installed() -> bool:
    return core.CONFIG.exists() and "key" in core.load_config()


def agent_command():
    if FROZEN:
        return [str(installed_exe()), "--run"]
    return [sys.executable, str(Path(__file__).resolve()), "--run"]


# ---------------------------------------------------------------- process control
def agent_running() -> bool:
    if IS_WIN:
        h = ctypes.windll.kernel32.OpenMutexW(0x00100000, False, MUTEX)  # SYNCHRONIZE
        if h:
            ctypes.windll.kernel32.CloseHandle(h)
            return True
        return False
    pid_file = app_dir() / "agent.pid"
    try:
        os.kill(int(pid_file.read_text()), 0)
        return True
    except (OSError, ValueError):
        return False


def start_agent():
    if agent_running():
        return
    subprocess.Popen(agent_command(), creationflags=DETACHED | NO_WINDOW, close_fds=True,
                     stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(50):
        if agent_running():
            return
        time.sleep(0.1)


def stop_agent():
    pid_file = app_dir() / "agent.pid"
    try:
        pid = int(pid_file.read_text())
    except (OSError, ValueError):
        return
    if IS_WIN:
        subprocess.run(["taskkill", "/F", "/FI", f"PID eq {pid}", "/FI", f"IMAGENAME eq {EXE_NAME}"],
                       creationflags=NO_WINDOW, capture_output=True)
        if not FROZEN:
            subprocess.run(["taskkill", "/F", "/PID", str(pid)], creationflags=NO_WINDOW, capture_output=True)
    else:
        try:
            os.kill(pid, 15)
        except OSError:
            pass
    for _ in range(50):
        if not agent_running():
            break
        time.sleep(0.1)
    time.sleep(0.3)  # let Windows release the exe file


def read_status():
    try:
        return json.loads(STATUS().read_text())
    except (OSError, ValueError):
        return {}


# ---------------------------------------------------------------- Windows integration
def _set_reg(path, values):
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, path) as k:
        for name, v in values.items():
            if isinstance(v, int):
                winreg.SetValueEx(k, name, 0, winreg.REG_DWORD, v)
            else:
                winreg.SetValueEx(k, name, 0, winreg.REG_SZ, v)


def register_windows():
    if not IS_WIN:
        return
    exe = str(installed_exe()) if FROZEN else sys.executable
    run_cmd = subprocess.list2cmdline(agent_command())
    _set_reg(RUN_KEY, {APP: run_cmd})
    _set_reg(UNINSTALL_KEY, {
        "DisplayName": "SelfDesk Agent", "DisplayVersion": VERSION, "Publisher": "SelfDesk",
        "DisplayIcon": exe, "InstallLocation": str(app_dir()),
        "UninstallString": subprocess.list2cmdline([exe, "--uninstall"]),
        "QuietUninstallString": subprocess.list2cmdline([exe, "--uninstall", "--quiet"]),
        "NoModify": 1, "NoRepair": 1,
    })
    if FROZEN:  # Start menu shortcut that opens the settings window
        lnk = Path(os.environ["APPDATA"]) / "Microsoft/Windows/Start Menu/Programs/SelfDesk.lnk"
        ps = ("$s=(New-Object -ComObject WScript.Shell).CreateShortcut($env:LNK);"
              "$s.TargetPath=$env:EXE;$s.Description='SelfDesk remote desktop agent';$s.Save()")
        subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", ps],
                       env={**os.environ, "LNK": str(lnk), "EXE": exe},
                       creationflags=NO_WINDOW, capture_output=True, timeout=30)


def unregister_windows():
    if not IS_WIN:
        return
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
            winreg.DeleteValue(k, APP)
    except OSError:
        pass
    try:
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, UNINSTALL_KEY)
    except OSError:
        pass
    try:
        (Path(os.environ["APPDATA"]) / "Microsoft/Windows/Start Menu/Programs/SelfDesk.lnk").unlink()
    except OSError:
        pass


# ---------------------------------------------------------------- install / uninstall
def copy_exe():
    """Put this exe into the install folder (also used to update to a newer exe)."""
    if not FROZEN:
        return
    src, dst = Path(sys.executable).resolve(), installed_exe()
    if dst.exists() and src == dst.resolve():
        return
    stop_agent()
    for attempt in range(20):
        try:
            shutil.copy2(src, dst)
            return
        except PermissionError:
            time.sleep(0.5)
    raise RuntimeError("Could not replace the installed agent (is it still running?)")


def install(server, token, name, password):
    server = server.strip().rstrip("/")
    if not server.startswith(("http://", "https://")):
        raise ValueError("Server URL must start with https:// (or http:// inside your home network)")
    if len(token.strip()) < 6:
        raise ValueError("Enter the enroll token from your server")
    if len(password) < 8:
        raise ValueError("Password must be at least 8 characters")
    app_dir().mkdir(parents=True, exist_ok=True)
    copy_exe()
    cfg = core.load_config()  # keeps the Device ID when reinstalling
    cfg.setdefault("id", str(core.secrets.randbelow(900_000_000) + 100_000_000))
    cfg.update(server=server, token=token.strip(), name=(name.strip() or socket.gethostname())[:64])
    cfg["key"] = core.derive_key(cfg["id"], password)
    core.save_config(cfg)
    try:
        STATUS().unlink()
    except OSError:
        pass
    register_windows()
    stop_agent()
    start_agent()
    return cfg


def change_password(password):
    if len(password) < 8:
        raise ValueError("Password must be at least 8 characters")
    cfg = core.load_config()
    cfg["key"] = core.derive_key(cfg["id"], password)
    core.save_config(cfg)
    stop_agent()
    start_agent()


def uninstall():
    stop_agent()
    unregister_windows()
    d = app_dir()
    for f in ("config.json", "status.json", "agent.pid", "agent.log", "agent.log.1"):
        try:
            (d / f).unlink()
        except OSError:
            pass
    if IS_WIN and FROZEN:
        # A running exe can't delete itself: remove the folder a few seconds after we exit.
        subprocess.Popen(f'cmd /c ping -n 4 127.0.0.1 >nul & rmdir /s /q "{d}"',
                         creationflags=DETACHED | NO_WINDOW, close_fds=True)
    else:
        shutil.rmtree(d, ignore_errors=True)


# ---------------------------------------------------------------- the hidden agent
def run_agent():
    d = app_dir()
    d.mkdir(parents=True, exist_ok=True)
    handler = logging.handlers.RotatingFileHandler(d / "agent.log", maxBytes=1_000_000,
                                                   backupCount=1, encoding="utf-8")
    logging.basicConfig(level=logging.INFO, handlers=[handler],
                        format="%(asctime)s %(levelname)s %(message)s")
    if IS_WIN:
        ctypes.windll.kernel32.CreateMutexW(None, False, MUTEX)
        if ctypes.windll.kernel32.GetLastError() == 183:  # ERROR_ALREADY_EXISTS
            return
        try:  # use real pixels for both screen capture and mouse position
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except Exception:
            pass
    (d / "agent.pid").write_text(str(os.getpid()))
    cfg = core.load_config()
    if "key" not in cfg:
        log.error("not set up yet; open SelfDeskAgent.exe to set it up")
        return

    def on_status(state, detail=""):
        tmp = STATUS().with_suffix(".tmp")
        tmp.write_text(json.dumps({"state": state, "detail": detail, "time": time.time()}))
        os.replace(tmp, STATUS())

    log.info("SelfDesk agent %s starting, device %s", VERSION, cfg["id"])
    on_status("connecting")
    asyncio.run(core.Agent(cfg, on_status=on_status).run_forever())


# ---------------------------------------------------------------- GUI
def load_defaults():
    here = Path(sys.executable if FROZEN else __file__).resolve().parent
    try:
        return json.loads((here / "selfdesk-defaults.json").read_text())
    except (OSError, ValueError):
        return {}


def fmt_id(i):
    return " ".join(i[n:n + 3] for n in range(0, len(i), 3))


def gui():
    import tkinter as tk
    import webbrowser
    from tkinter import messagebox, simpledialog, ttk

    if IS_WIN:
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    root = tk.Tk()
    root.title("SelfDesk")
    root.resizable(False, False)
    if IS_WIN and FROZEN:
        try:
            root.iconbitmap(default=sys.executable)
        except tk.TclError:
            pass
    style = ttk.Style()
    if "vista" in style.theme_names():
        style.theme_use("vista")
    style.configure("Big.TLabel", font=("Segoe UI", 26, "bold"))
    style.configure("H.TLabel", font=("Segoe UI", 14, "bold"))
    style.configure("Muted.TLabel", foreground="#667085")
    frame = ttk.Frame(root, padding=22)
    frame.pack(fill="both", expand=True)

    def clear():
        for w in frame.winfo_children():
            w.destroy()

    def center():
        root.update_idletasks()
        w, h = root.winfo_width(), root.winfo_height()
        root.geometry(f"+{(root.winfo_screenwidth() - w) // 2}+{(root.winfo_screenheight() - h) // 3}")

    # ---------- setup ----------
    def setup_screen():
        clear()
        d = {**load_defaults(), **{k: v for k, v in core.load_config().items() if k in ("server", "token", "name")}}
        locked = bool(load_defaults().get("lock")) and d.get("server") and d.get("token")
        ttk.Label(frame, text="Set up SelfDesk on this PC", style="H.TLabel").grid(columnspan=2, sticky="w")
        ttk.Label(frame, text="This PC will be reachable from your SelfDesk server.\n"
                              "It starts automatically with Windows. No admin rights needed.",
                  style="Muted.TLabel").grid(columnspan=2, sticky="w", pady=(2, 14))
        fields = {}
        rows = [("server", "Server URL", False), ("token", "Enroll token", True),
                ("name", "Name for this PC", False), ("pw", "Password for this PC", True),
                ("pw2", "Repeat password", True)]
        for key, label, secret in rows:
            if locked and key in ("server", "token"):
                continue
            ttk.Label(frame, text=label).grid(column=0, sticky="w", pady=4, padx=(0, 12))
            e = ttk.Entry(frame, width=40, show="•" if secret else "")
            e.grid(column=1, row=frame.grid_size()[1] - 1, pady=4)
            e.insert(0, {"name": d.get("name") or socket.gethostname()}.get(key, d.get(key, "")))
            fields[key] = e
        err = ttk.Label(frame, text="", foreground="#d64545")
        err.grid(columnspan=2, sticky="w", pady=(6, 0))
        btn = ttk.Button(frame, text="Install and start")
        btn.grid(columnspan=2, sticky="e", pady=(12, 0))

        def do_install(_=None):
            get = lambda k: fields[k].get() if k in fields else d.get(k, "")  # noqa: E731
            if get("pw") != get("pw2"):
                err.config(text="Passwords do not match")
                return
            btn.config(state="disabled")
            err.config(text="Installing…", foreground="#667085")
            root.update()
            try:
                install(get("server"), get("token"), get("name"), get("pw"))
            except Exception as e:  # show any problem to the user
                err.config(text=str(e), foreground="#d64545")
                btn.config(state="normal")
                return
            manage_screen(just_installed=True)

        btn.config(command=do_install)
        root.bind("<Return>", do_install)
        next(iter(fields.values())).focus_set()
        center()

    # ---------- manage ----------
    def manage_screen(just_installed=False):
        clear()
        root.unbind("<Return>")
        cfg = core.load_config()
        ttk.Label(frame, text="SelfDesk is installed ✔" if just_installed else "SelfDesk",
                  style="H.TLabel").grid(columnspan=3, sticky="w")
        ttk.Label(frame, text="Device ID", style="Muted.TLabel").grid(columnspan=3, sticky="w", pady=(14, 0))
        ttk.Label(frame, text=fmt_id(cfg["id"]), style="Big.TLabel").grid(column=0, columnspan=2, sticky="w")

        def copy_id():
            root.clipboard_clear()
            root.clipboard_append(cfg["id"])
            copy_btn.config(text="Copied")

        copy_btn = ttk.Button(frame, text="Copy ID", command=copy_id)
        copy_btn.grid(column=2, row=2, sticky="e")
        info = ttk.Label(frame, text=f"Name: {cfg.get('name')}\nServer: {cfg.get('server')}", style="Muted.TLabel")
        info.grid(columnspan=3, sticky="w", pady=(6, 0))
        status = ttk.Label(frame, text="", font=("Segoe UI", 10, "bold"))
        status.grid(columnspan=3, sticky="w", pady=(10, 14))

        def refresh():
            if not status.winfo_exists():
                return
            st = read_status()
            if not agent_running():
                status.config(text="● Stopped", foreground="#d64545")
            elif st.get("state") == "online":
                status.config(text="● Online: ready for remote connections", foreground="#1f9d55")
            elif st.get("state") == "error":
                status.config(text=f"● Problem: {st.get('detail')}", foreground="#d64545")
            elif st.get("state") == "offline":
                status.config(text="● Can't reach the server, retrying…", foreground="#c77700")
            else:
                status.config(text="● Connecting…", foreground="#c77700")
            root.after(1500, refresh)

        refresh()
        bar = ttk.Frame(frame)
        bar.grid(columnspan=3, sticky="ew")

        def pw():
            p1 = simpledialog.askstring("Change password", "New password (8+ characters):", show="•", parent=root)
            if p1 is None:
                return
            p2 = simpledialog.askstring("Change password", "Repeat new password:", show="•", parent=root)
            if p1 != p2:
                messagebox.showerror("SelfDesk", "Passwords do not match", parent=root)
                return
            try:
                change_password(p1)
                messagebox.showinfo("SelfDesk", "Password changed.", parent=root)
            except ValueError as e:
                messagebox.showerror("SelfDesk", str(e), parent=root)

        def restart():
            stop_agent()
            start_agent()

        def remove():
            if messagebox.askyesno("Uninstall SelfDesk",
                                   "Remove SelfDesk from this PC?\nIt will no longer be reachable remotely.",
                                   parent=root):
                uninstall()
                messagebox.showinfo("SelfDesk", "SelfDesk was removed.", parent=root)
                root.destroy()

        def update():
            try:
                copy_exe()
                register_windows()
                start_agent()
                messagebox.showinfo("SelfDesk", f"Updated to version {VERSION}.", parent=root)
                manage_screen()
            except Exception as e:
                messagebox.showerror("SelfDesk", str(e), parent=root)

        buttons = [("Change password", pw), ("Restart", restart),
                   ("Open web viewer", lambda: webbrowser.open(cfg.get("server", ""))),
                   ("Settings…", setup_screen), ("Uninstall", remove)]
        if FROZEN and Path(sys.executable).resolve() != installed_exe().resolve():
            buttons.insert(0, (f"Update to {VERSION}", update))
        for i, (t, fn) in enumerate(buttons):
            ttk.Button(bar, text=t, command=fn).grid(row=0, column=i, padx=(0, 6))
        ttk.Label(frame, text=f"Version {VERSION}", style="Muted.TLabel").grid(columnspan=3, sticky="e", pady=(12, 0))
        if not agent_running():
            start_agent()
        center()

    (manage_screen if is_installed() else setup_screen)()
    root.mainloop()


# ---------------------------------------------------------------- entry
def main():
    ap = argparse.ArgumentParser(prog="SelfDeskAgent")
    ap.add_argument("--run", action="store_true")
    ap.add_argument("--install", action="store_true")
    ap.add_argument("--uninstall", action="store_true")
    ap.add_argument("--quiet", action="store_true")
    ap.add_argument("--server")
    ap.add_argument("--token")
    ap.add_argument("--name", default="")
    ap.add_argument("--password")

    def bad_args(msg):  # no console in the exe: raise so it lands in crash.log
        raise ValueError(f"Bad command line: {msg}\n\n{ap.format_usage()}")

    ap.error = bad_args
    a = ap.parse_args()

    if a.run:
        run_agent()
    elif a.install:
        d = load_defaults()
        try:
            cfg = install(a.server or d.get("server", ""), a.token or d.get("token", ""),
                          a.name, a.password or "")
        except Exception as e:
            # the .exe has no console, so also leave the reason in a file
            app_dir().mkdir(parents=True, exist_ok=True)
            (app_dir() / "install-error.txt").write_text(str(e))
            if sys.stderr:
                print(f"SelfDesk install failed: {e}", file=sys.stderr)
            sys.exit(1)
        if sys.stdout:
            print(f"SelfDesk installed. Device ID: {cfg['id']}")
    elif a.uninstall:
        if a.quiet:
            uninstall()
        else:
            import tkinter as tk
            from tkinter import messagebox
            r = tk.Tk()
            r.withdraw()
            if messagebox.askyesno("Uninstall SelfDesk", "Remove SelfDesk from this PC?"):
                uninstall()
                messagebox.showinfo("SelfDesk", "SelfDesk was removed.")
    else:
        gui()


if __name__ == "__main__":
    main()
