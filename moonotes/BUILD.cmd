@echo off
setlocal
cd /d "%~dp0"
call "gradlew.bat" clean build
if errorlevel 1 (
 echo BUILD FAILED. See compiler errors above.
 pause
 exit /b 1
)
echo BUILD COMPLETE. See build\libs.
pause
