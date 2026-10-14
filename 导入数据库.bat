@echo off
chcp 65001 >nul
title 城科酒店管理系统 - 导入数据库
cd /d "%~dp0"

echo ============================================
echo   导入数据库（只需执行一次）
echo   会重建 hotel_system 库，原有演示数据将被重置
echo ============================================
echo.

REM ===== Local credentials (DB user / password) =====
REM Real values live in demo-secrets.local.bat, which .gitignore excludes.
set MYSQL_BIN=D:\mysql-8.0\bin\mysql.exe
set HOTEL_DB_USER=root
if exist "demo-secrets.local.bat" call "demo-secrets.local.bat"
set DEFAULT_MYSQL_BIN=%MYSQL_BIN%

set /p MYSQL_BIN=请输入 mysql.exe 的完整路径（直接回车用默认 %DEFAULT_MYSQL_BIN%）:
if "%MYSQL_BIN%"=="" set MYSQL_BIN=%DEFAULT_MYSQL_BIN%
if not exist "%MYSQL_BIN%" goto NOBIN

if not "%HOTEL_DB_PASSWORD%"=="" goto HAVEPWD
set /p HOTEL_DB_PASSWORD=请输入 MySQL 账号 %HOTEL_DB_USER% 的口令:
:HAVEPWD

echo.
echo 开始导入 sql\hotel_system.sql ...
"%MYSQL_BIN%" -u%HOTEL_DB_USER% -p%HOTEL_DB_PASSWORD% --default-character-set=utf8mb4 < "sql\hotel_system.sql"
if errorlevel 1 goto IMPORTFAIL

echo.
echo 导入完成。注意：脚本里的账号口令是占位符，
echo 需要先启动一次系统（一键启动.bat）才会写入真实口令散列，之后才能登录。
pause
exit /b 0

:NOBIN
echo 找不到 %MYSQL_BIN%，请确认 MySQL 安装路径。
pause
exit /b 1

:IMPORTFAIL
echo.
echo 导入失败：请检查 MySQL 是否已启动、账号口令是否正确。
echo 口令配置在 demo-secrets.local.bat（可参考 demo-secrets.example.bat 创建）。
echo 若口令不同，请手动执行：
echo   mysql -u你的账号 -p你的口令 --default-character-set=utf8mb4 ^< sql\hotel_system.sql
pause
exit /b 1
