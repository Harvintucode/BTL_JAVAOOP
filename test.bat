@echo off
setlocal
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
if not exist "build" mkdir "build"
(for %%F in ("src\cafe\*.java") do echo "src/cafe/%%~nxF") > "build\sources.txt"
javac -encoding UTF-8 -d "build" @"build\sources.txt"
if errorlevel 1 goto failed
(for %%F in ("tests\cafe\*.java") do echo "tests/cafe/%%~nxF") > "build\tests.txt"
javac -encoding UTF-8 -cp "build" -d "build" @"build\tests.txt"
if errorlevel 1 goto failed
java -cp "build" cafe.SmokeTest
if errorlevel 1 goto failed
java -cp "build" cafe.AuthTest
if errorlevel 1 goto failed
if /i not "%~1"=="--no-pause" pause
exit /b 0

:failed
echo [ERROR] Bien dich hoac kiem thu that bai.
if /i not "%~1"=="--no-pause" pause
exit /b 1
