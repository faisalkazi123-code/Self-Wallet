@echo off
REM SelfDesk agent setup for Windows. Run from this folder.
REM Needs Python 3.10+ from https://www.python.org (tick "Add python.exe to PATH").
cd /d "%~dp0"
python -m pip install --upgrade -r requirements.txt || goto :error
if not exist config.json (
  set /p SERVER=Server URL ^(e.g. https://desk.example.com^): 
  set /p TOKEN=Enroll token: 
  set /p NAME=Name for this PC: 
)
if not exist config.json (
  python agent.py --server "%SERVER%" --token "%TOKEN%" --name "%NAME%" --setup-only || goto :error
)
REM Start automatically when this user logs in (runs hidden, no console window)
for /f "delims=" %%P in ('python -c "import sys,os;print(os.path.join(os.path.dirname(sys.executable),'pythonw.exe'))"') do set PYW=%%P
schtasks /create /f /sc onlogon /rl highest /tn "SelfDesk Agent" /tr "\"%PYW%\" \"%~dp0agent.py\"" || goto :error
schtasks /run /tn "SelfDesk Agent"
echo.
echo Done. SelfDesk will start every time you log in. Device ID is in config.json.
type config.json | findstr "\"id\""
pause
exit /b 0
:error
echo Setup failed.
pause
exit /b 1
