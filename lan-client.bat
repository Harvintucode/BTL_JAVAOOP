@echo off
rem Dung chung cho run-pos-lan.bat va run-admin-lan.bat. Tham so 1: lop can chay (cafe.PosApp / cafe.AdminApp).
setlocal EnableDelayedExpansion
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Khong tim thay javac. Hay cai JDK 17+ va them JDK\bin vao PATH.
  pause
  exit /b 1
)
set "SERVER="
if exist "server.txt" set /p SERVER=<"server.txt"
if not defined SERVER (
  echo Chua co dia chi may chu. Hay xem dia chi hien tren cua so run-server.bat.
  set /p SERVER=Nhap IP may chu, vi du 192.168.1.10 hoac 192.168.1.10:8080:
  if not defined SERVER (
    echo [ERROR] Chua nhap dia chi may chu.
    pause
    exit /b 1
  )
  >"server.txt" echo(!SERVER!
  echo Da luu vao server.txt. Muon doi may chu: sua hoac xoa file server.txt.
)
if not exist "build" mkdir "build"
(for %%F in ("src\cafe\*.java") do echo "src/cafe/%%~nxF") > "build\sources.txt"
javac -encoding UTF-8 -d "build" @"build\sources.txt"
if errorlevel 1 (
  echo [ERROR] Bien dich that bai.
  pause
  exit /b 1
)
echo Dang ket noi toi may chu !SERVER! ...
java "-Dcafe.server=!SERVER!" -cp "%CD%\build" %1
if errorlevel 1 pause
