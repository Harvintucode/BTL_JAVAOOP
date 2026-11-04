@echo off
setlocal
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
title Cotafe Server (LAN)
where javac >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Khong tim thay javac. Hay cai JDK 17+ va them JDK\bin vao PATH.
  pause
  exit /b 1
)
if not exist "build" mkdir "build"
echo [1/2] Compiling Java sources...
(for %%F in ("src\cafe\*.java") do echo "src/cafe/%%~nxF") > "build\sources.txt"
javac -encoding UTF-8 -d "build" @"build\sources.txt"
if errorlevel 1 (
  echo [ERROR] Bien dich that bai.
  pause
  exit /b 1
)
echo [2/2] Starting server... (cong mac dinh 8080, doi cong: run-server.bat 9000)
echo.
java "-Dcafe.home=%CD%" -cp "%CD%\build" cafe.ServerMain %1
echo.
echo Server da dung.
pause
