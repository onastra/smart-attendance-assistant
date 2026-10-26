@echo off
REM ============================================================
REM  数据库恢复（MySQL 5.7）
REM ------------------------------------------------------------
REM  ⚠ 危险操作：恢复会覆盖现有数据（schema.sql 用 DROP TABLE IF EXISTS）。
REM     脚本会要求手工输入文件名确认，不接受"默认恢复最近一份"，
REM     避免误操作把演示数据覆盖成旧备份。
REM
REM  用法：
REM    scripts\restore-db.cmd data\backup\lianzhi-20261026-180000.sql
REM ============================================================
setlocal

set "SRC=%~1"
if "%SRC%"=="" (
  echo [ERROR] 请指定备份文件：scripts\restore-db.cmd data\backup\lianzhi-YYYYMMDD-HHMMSS.sql
  exit /b 1
)
if not exist "%SRC%" (
  echo [ERROR] 文件不存在：%SRC%
  exit /b 1
)

if "%LZ_DB_HOST%"=="" ( set "DBHOST=127.0.0.1" ) else ( set "DBHOST=%LZ_DB_HOST%" )
if "%LZ_DB_PORT%"=="" ( set "DBPORT=3306" ) else ( set "DBPORT=%LZ_DB_PORT%" )
if "%LZ_DB_USER%"=="" ( set "DBUSER=root" ) else ( set "DBUSER=%LZ_DB_USER%" )
if "%LZ_DB_PASSWORD%"=="" ( set "DBPASS=" ) else ( set "DBPASS=%LZ_DB_PASSWORD%" )
set "DBNAME=lianzhi"

echo ============================================================
echo  WARNING: 即将把 %SRC% 恢复到 %DBHOST%/%DBNAME%
echo           现有数据将被覆盖。
echo ============================================================
set "CONFIRM="
set /p CONFIRM=输入 YES 继续：
if /i not "%CONFIRM%"=="YES" (
  echo [INFO ] 已取消。
  exit /b 0
)

echo [INFO ] Restoring ...
if "%DBPASS%"=="" (
  mysql -h %DBHOST% -P %DBPORT% -u %DBUSER% --default-character-set=utf8mb4 %DBNAME% < "%SRC%"
) else (
  mysql -h %DBHOST% -P %DBPORT% -u %DBUSER% -p%DBPASS% --default-character-set=utf8mb4 %DBNAME% < "%SRC%"
)

if errorlevel 1 (
  echo [ERROR] restore failed.
  exit /b 1
)
echo [INFO ] Restore done.
endlocal
