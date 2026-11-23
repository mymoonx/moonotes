@echo off
cd /d "%~dp0"
call npm.cmd ci
if errorlevel 1 goto error
call npm.cmd test
if errorlevel 1 goto error
call npx.cmd wrangler deploy
if errorlevel 1 goto error
echo Worker updated successfully.
pause
exit /b 0
:error
echo Deployment failed. See output above.
pause
exit /b 1
