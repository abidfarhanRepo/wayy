@echo off
setlocal enabledelayedexpansion
cd /d "%USERPROFILE%\.ssh"
echo Generating SSH key...
echo | ssh-keygen -t ed25519 -f wayy_ed25519 -N "" -C wayy-dev
echo.
echo Key generation complete. Files:
dir wayy_ed25519*
echo.
echo Public key content:
type wayy_ed25519.pub
