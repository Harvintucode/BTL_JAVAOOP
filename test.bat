@echo off
setlocal
cd /d "%~dp0"
if not exist "build" mkdir "build"
javac -encoding UTF-8 -d "build" "src\cafe\Data.java" "src\cafe\Catalog.java" "src\cafe\Store.java" "src\cafe\CafeService.java" "src\cafe\Ui.java" "src\cafe\PosApp.java" "src\cafe\AdminApp.java"
if errorlevel 1 (pause & exit /b 1)
javac -encoding UTF-8 -cp "build" -d "build" "tests\cafe\SmokeTest.java"
if errorlevel 1 (pause & exit /b 1)
java -cp "build" cafe.SmokeTest
pause
