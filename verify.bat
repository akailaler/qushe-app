@echo off
REM qushe verify - double click to run
REM Uses WorkBuddy's bundled Python so it does not depend on system python.
REM NOTE: keep this file ASCII-only. Chinese in .bat breaks under cmd/GBK.

set PY=C:\Users\22120\.workbuddy\binaries\python\versions\3.13.12\python.exe
if not exist "%PY%" set PY=python

"%PY%" "%~dp0verify.py" %*

echo.
if errorlevel 1 pause
