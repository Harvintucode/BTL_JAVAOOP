@echo off
setlocal
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
title Cotafe - Run POS and Admin
where javac >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Khong tim thay javac. Hay cai JDK 17+ va them JDK\bin vao PATH.
  pause
  exit /b 1
)
where javaw >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Khong tim thay javaw. Hay cai JDK 17+.
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
echo [2/2] Starting two Java apps...
start "Cotafe POS" javaw "-Dcafe.home=%CD%" -cp "%CD%\build" cafe.PosApp
start "Cotafe Admin" javaw "-Dcafe.home=%CD%" -cp "%CD%\build" cafe.AdminApp
echo OK! Dang nhap POS va Admin trong hai cua so rieng.
echo Du lieu dung chung: %CD%\cafe.dat
echo Tai khoan dung chung: %CD%\users.dat
pause
