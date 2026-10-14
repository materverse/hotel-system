@echo off
chcp 65001 >nul
title 城科酒店管理系统 - 启动
cd /d "%~dp0"

echo ============================================
echo   城科酒店管理系统 启动脚本
echo ============================================
echo.

REM ===== Local credentials (DB password + model API key) =====
REM Real values live in demo-secrets.local.bat, which .gitignore excludes.
REM First run: copy demo-secrets.example.bat to demo-secrets.local.bat and fill in your values.
set HOTEL_DB_USER=root
set HOTEL_DB_PASSWORD=
set HOTEL_AI_BASE_URL=https://api.deepseek.com
set HOTEL_AI_MODEL=deepseek-chat
set HOTEL_AI_PROVIDER=deepseek
if not exist "demo-secrets.local.bat" goto NOLOCALCRED
call "demo-secrets.local.bat"
goto CREDDONE
:NOLOCALCRED
echo [INFO] demo-secrets.local.bat not found - starting with defaults (AI assistant disabled)
:CREDDONE

echo [1/4] 检查数据库连接...
echo [2/4] 检查启动包...

if exist "backend\target\hotel-system.jar" goto JARREADY
echo     未找到启动包，开始编译（首次需要几分钟，会联网下载依赖）...
cd backend
call mvnw.cmd -DskipTests clean package
if errorlevel 1 goto BUILDFAIL
cd ..
goto JARREADY

:BUILDFAIL
echo.
echo 编译失败：请确认已安装 JDK 17 与 Maven，或直接在 IntelliJ IDEA 中运行 HotelSystemApplication。
pause
exit /b 1

:JARREADY
echo [3/4] 启动后端服务（端口 8080）...
echo     浏览器将自动打开 http://localhost:8080
echo     关闭本窗口即可停止服务。
echo.
start "" cmd /c "timeout /t 12 >nul & start http://localhost:8080"
cd backend
java -Dfile.encoding=UTF-8 -jar target\hotel-system.jar

echo.
echo 服务已停止。
pause
