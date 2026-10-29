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
set "CLOUD_CFG=%~dp0..\server\lz-app\src\main\resources\application-cloud.yml"

REM --- 私密配置走"外部文件"加载（jar 内已由 pom 排除，防密钥进产物）----
REM 把 resources 目录注册为额外配置目录：local/cloud yml 均从该目录按文件读取。
REM 反斜杠替换成正斜杠，避免 file: URL 在 Windows 下被当转义序列。
set "RES_DIR=%~dp0..\server\lz-app\src\main\resources\"
set "RES_URL=file:%RES_DIR:\=/%"
set "LOCATION_ARG=--spring.config.additional-location=%RES_URL%"

REM --- 自动激活 local profile（私密配置存在时）--------------
if exist "%LOCAL_CFG%" (
  set "PROFILE_ARG=--spring.profiles.active=local"
  echo [INFO ] Found %LOCAL_CFG% - activating 'local' profile.
) else (
  set "PROFILE_ARG="
  echo [WARN ] application-local.yml not found - using defaults.
  echo         Copy application-local.yml.example and fill in your DB password.
)

REM --- 追加激活 cloud profile（共享 Supabase 数据源存在时）--
REM cloud 覆盖 local 里的数据源（local 现在只放 LLM/地图等本机能力配置）
REM 注意：local 缺失时 PROFILE_ARG 为空，直接拼会导致 ",cloud" 被当成非选项参数
REM （cloud 静默失效、看起来还启动成功），所以这里分两种情况处理。
if exist "%CLOUD_CFG%" (
  if "%PROFILE_ARG%"=="" (
    set "PROFILE_ARG=--spring.profiles.active=cloud"
  ) else (
    set "PROFILE_ARG=%PROFILE_ARG%,cloud"
  )
  echo [INFO ] Found application-cloud.yml - activating 'cloud' profile for shared Supabase datasource.
)

echo [INFO ] Starting backend on port %APP_PORT% ...
echo [INFO ] Jar: %JAR%
echo.

java -jar "%JAR%" --server.port=%APP_PORT% %PROFILE_ARG% %LOCATION_ARG%

endlocal
