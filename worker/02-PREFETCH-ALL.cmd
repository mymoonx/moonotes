@echo off
cd /d "%~dp0"
node sync-r2.mjs
if errorlevel 1 (
  echo Some files failed. Read v0kky-sync-report.json and rerun this command.
) else (
  echo All 7TV channel images have been cached on Cloudflare R2.
)
pause
