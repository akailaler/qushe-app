@echo off
REM qushe verify - double click to run
REM NOTE: keep this file ASCII-only. Chinese in .bat breaks under cmd/GBK.

REM 优先用 PATH 里的 python，找不到再退到 py 启动器。
REM 不要在这里写本机绝对路径 —— 仓库是公开的，别人 clone 下来也得能跑。
set "PY=python"
where python >nul 2>nul || set "PY=py"

"%PY%" "%~dp0verify.py" %*

echo.
if errorlevel 1 pause
