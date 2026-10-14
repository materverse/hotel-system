@echo off
REM ============================================================
REM  CREDENTIALS TEMPLATE
REM  1. copy this file to  demo-secrets.local.bat
REM  2. fill in your own values
REM  demo-secrets.local.bat is excluded by .gitignore and will
REM  never be committed - keep it that way.
REM ============================================================

REM ---------- database ----------
set HOTEL_DB_USER=root
set HOTEL_DB_PASSWORD=YOUR_DB_PASSWORD
set MYSQL_BIN=D:\mysql-8.0\bin\mysql.exe

REM ---------- AI model (OpenAI-compatible, DeepSeek by default) ----------
set HOTEL_AI_PROVIDER=deepseek
set HOTEL_AI_BASE_URL=https://api.deepseek.com
set HOTEL_AI_MODEL=deepseek-chat
set HOTEL_AI_API_KEY=YOUR_OWN_API_KEY
