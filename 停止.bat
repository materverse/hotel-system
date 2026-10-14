@echo off
chcp 65001 >nul
title 城科酒店管理系统 - 停止服务
cd /d "%~dp0"
echo 正在停止占用 8080 端口的后端进程...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    echo   kill PID %%a
    taskkill /F /PID %%a >nul 2>&1
)

echo.
echo 已停止。数据库（MySQL 服务）不受影响。
pause
