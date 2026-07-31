@echo off
cd /d "%~dp0"
echo ============================================
echo  VPet Waifu - RuStore signing key export
echo ============================================
echo.
echo Password for BOTH prompts (copy it now):
echo.
echo    8t5pq61Y8PdxePGe1bQp
echo.
echo Tip: select the password with the mouse and press Enter to copy,
echo then right-click to paste at each prompt.
echo.
java -jar pepk.jar --keystore release.keystore --alias vpetwaifu --output pepk_out.zip --encryptionkey=0000a9d0e2b3cc010a0fd81879d8a72c954b044af3955029547602798ae08748787c2f612d2bbfd67541afd1e48c23f6839e24463ff87fa4cf614d5c54fbbe5caa025709 --include-cert
echo.
if exist pepk_out.zip (echo SUCCESS: pepk_out.zip created - upload it to RuStore step 3) else (echo FAILED - check that Java is installed and files are in this folder)
pause
