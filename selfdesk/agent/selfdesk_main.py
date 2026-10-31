"""Entry point of SelfDeskAgent.exe: runs the app and records any crash.

The exe has no console window, so a startup error would otherwise vanish
silently. It is written to %LOCALAPPDATA%\\SelfDesk\\crash.log and shown in a
message box (except in hidden/quiet modes).
"""
import os
import sys
import traceback
from pathlib import Path


def report(text):
    d = Path(os.environ.get("LOCALAPPDATA") or Path.home()) / "SelfDesk"
    try:
        d.mkdir(parents=True, exist_ok=True)
        (d / "crash.log").write_text(text, encoding="utf-8")
    except OSError:
        pass
    if sys.stderr:
        print(text, file=sys.stderr)
    if sys.platform == "win32" and not ({"--run", "--quiet", "--install"} & set(sys.argv)):
        import ctypes
        ctypes.windll.user32.MessageBoxW(None, f"SelfDesk could not start:\n\n{text[-1500:]}\n\n"
                                         f"Details: {d / 'crash.log'}", "SelfDesk", 0x10)


if __name__ == "__main__":
    try:
        import windows_app
        windows_app.main()
    except SystemExit:
        raise
    except BaseException:
        report(traceback.format_exc())
        sys.exit(1)
