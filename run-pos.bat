@echo off
setlocal
cd /d "%~dp0"
if not exist "build\cafe\PosApp.class" (
  echo Ban can chay run.bat mot lan de bien dich truoc.
  pause
  exit /b 1
)
java "-Dcafe.home=%CD%" -cp "%CD%\build" cafe.PosApp
if errorlevel 1 pause
