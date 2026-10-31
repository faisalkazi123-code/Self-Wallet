@echo off
REM Builds dist\SelfDeskAgent.exe on a Windows PC (GitHub Actions does the same automatically).
cd /d "%~dp0"
python -m pip install -r requirements.txt pyinstaller || exit /b 1
python make_icon.py icon.ico || exit /b 1
python -m PyInstaller --noconfirm --onefile --noconsole --name SelfDeskAgent --icon icon.ico ^
  --hidden-import pynput.keyboard._win32 --hidden-import pynput.mouse._win32 windows_app.py || exit /b 1
echo Built dist\SelfDeskAgent.exe
