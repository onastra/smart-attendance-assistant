@echo off
REM ============================================================
REM  数据库备份（MySQL 5.7）
REM ------------------------------------------------------------
REM  用法：
REM    scripts\backup-db.cmd                 备份到 data\backup\lianzhi-YYYYMMDD-HHMMSS.sql
REM    set LZ_DB_HOST=127.0.0.1
REM    set LZ_DB_PORT=3306
REM    set LZ_DB_USER=root
REM    set LZ_DB_PASSWORD=gjc
REM    scripts\backup-db.cmd
REM
REM  备份内容：结构 + 数据（--routines 不含，本项目没有存储过程）。
REM  default-character-set 用 utf8mb4，避免中文注释与数据变乱码。
REM ============================================================
setlocal

if "%LZ_DB_HOST%"=="" ( set "DBHOST=127.0.0.1" ) else ( set "DBHOST=%LZ_DB_HOST%" )
if "%LZ_DB_PORT%"=="" ( set "DBPORT=3306" ) else ( set "DBPORT=%LZ_DB_PORT%" )
if "%LZ_DB_USER%"=="" ( set "DBUSER=root" ) else ( set "DBUSER=%LZ_DB_USER%" )
if "%LZ_DB_PASSWORD%"=="" ( set "DBPASS=" ) else ( set "DBPASS=%LZ_DB_PASSWORD%" )
set "DBNAME=lianzhi"

set "STAMP=%date:~0,4%%date:~5,2%%date:~8,2%-%time:~0,2%%time:~3,2%%time:~6,2%"
set "STAMP=%STAMP: =0%"
set "OUTDIR=%~dp0..\data\backup"
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
set "OUTFILE=%OUTDIR%\%DBNAME%-%STAMP%.sql"

echo [INFO ] Backing up %DBNAME% from %DBHOST%:%DBPORT% ...
echo [INFO ] Output: %OUTFILE%

if "%DBPASS%"=="" (
  mysqldump -h %DBHOST% -P %DBPORT% -u %DBUSER% --default-character-set=utf8mb4 --single-transaction --quick %DBNAME% > "%OUTFILE%"
) else (
  mysqldump -h %DBHOST% -P %DBPORT% -u %DBUSER% -p%DBPASS% --default-character-set=utf8mb4 --single-transaction --quick %DBNAME% > "%OUTFILE%"
)

if errorlevel 1 (
  echo [ERROR] mysqldump failed. 请确认 MySQL 在跑，且 mysqldump 在 PATH 中。
  exit /b 1
)

echo [INFO ] Backup done.
for %%F in ("%OUTFILE%") do echo [INFO ] Size: %%~zF bytes
endlocal
