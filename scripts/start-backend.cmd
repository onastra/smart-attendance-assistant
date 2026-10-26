@echo off
REM ============================================================
REM  Lianzhi Attendance - backend launcher
REM ============================================================
REM  Why this script exists:
REM    Spring Boot relaxed-binding maps environment variables onto
REM    config keys. If the shell happens to export SERVER__PORT,
REM    SERVER_PORT or PORT, Spring would silently override
REM    server.port (we hit SERVER__PORT=56043 once). Clear them and
REM    pass the port explicitly so the port is always deterministic.
REM
REM  Usage:
REM    scripts\start-backend.cmd            (uses port 8080)
REM    set LZ_SERVER_PORT=8088 && scripts\start-backend.cmd
REM ============================================================

setlocal

REM --- neutralise accidental port env vars -------------------
set SERVER__PORT=
set SERVER_PORT=
set PORT=

if "%LZ_SERVER_PORT%"=="" ( set "APP_PORT=8080" ) else ( set "APP_PORT=%LZ_SERVER_PORT%" )

set "JAR=%~dp0..\server\lz-app\target\lz-app.jar"

if not exist "%JAR%" (
  echo [ERROR] Jar not found: %JAR%
  echo         Build it first:  cd server ^&^& mvn -DskipTests package
  exit /b 1
)

set "LOCAL_CFG=%~dp0..\server\lz-app\src\main\resources\application-local.yml"

REM --- 自动激活 local profile（私密配置存在时）--------------
if exist "%LOCAL_CFG%" (
  set "PROFILE_ARG=--spring.profiles.active=local"
  echo [INFO ] Found %LOCAL_CFG% - activating 'local' profile.
) else (
  set "PROFILE_ARG="
  echo [WARN ] application-local.yml not found - using defaults.
  echo         Copy application-local.yml.example and fill in your DB password.
)

echo [INFO ] Starting backend on port %APP_PORT% ...
echo [INFO ] Jar: %JAR%
echo.

java -jar "%JAR%" --server.port=%APP_PORT% %PROFILE_ARG%

endlocal
