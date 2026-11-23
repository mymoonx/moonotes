@echo off
cd /d "%~dp0"
if not exist "v0kky-manifest.json" (
  echo ERROR: Place your existing v0kky-manifest.json in this directory first.
  echo The old sync-r2.mjs generated it during the 704 emote synchronization.
  pause
  exit /b 1
)
call npx.cmd wrangler r2 object put "moonotes-emotes/manifests/7tv/v0kky.json" --file "v0kky-manifest.json" --content-type "application/json" --remote
if errorlevel 1 (
  echo Upload failed. Make sure Wrangler is logged in.
  pause
  exit /b 1
)
echo Manifest uploaded. Open https://moonotes-emotes.thefreelom.workers.dev/v1/7tv/channel/v0kky
pause
