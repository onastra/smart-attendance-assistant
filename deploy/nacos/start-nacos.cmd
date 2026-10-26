@echo off
REM ============================================================
REM  本地 Nacos 2.x 启动脚本（可选能力，默认不启用）
REM ------------------------------------------------------------
REM  现状：项目是模块化单体，Nacos 处于"已配置未启用"状态：
REM        lz.nacos.enabled=false，代码里没有必须依赖注册中心的路径。
REM  这个脚本只用于在本地把 Nacos 跑起来做验证/演示，
REM  启用开关之前请先确认：改了它不会影响单体部署形态。
REM
REM  用法：
REM    deploy\nacos\start-nacos.cmd           启动（standalone 单机模式）
REM    deploy\nacos\start-nacos.cmd stop      停止并删除容器
REM ============================================================
setlocal

set "MODE=%~1"
set "CONTAINER=lz-nacos"

if /i "%MODE%"=="stop" (
  echo [INFO ] Stopping Nacos container %CONTAINER% ...
  docker stop %CONTAINER% >nul 2>&1
  docker rm %CONTAINER% >nul 2>&1
  echo [INFO ] Done.
  exit /b 0
)

echo [INFO ] Starting Nacos 2.x (standalone) ...
echo [INFO ] Console: http://127.0.0.1:8848/nacos   (nacos / nacos)
echo.

docker run -d ^
  --name %CONTAINER% ^
  -e MODE=standalone ^
  -e NACOS_AUTH_TOKEN= ^
  -e JVM_XMS=256m -e JVM_XMX=512m ^
  -p 8848:8848 ^
  -p 9848:9848 ^
  nacos/nacos-server:v2.3.2

if errorlevel 1 (
  echo [ERROR] docker run failed. 请先确认 Docker 引擎已启动。
  exit /b 1
)

echo.
echo [INFO ] Nacos 已启动。等待约 30 秒初始化后访问 http://127.0.0.1:8848/nacos
echo [INFO ] 要让应用真正连上它，需要同时：
echo         1) 设置环境变量 LZ_NACOS_ENABLED=true
echo         2) 在代码中引入服务注册/配置中心依赖（当前单体架构未引入，不做"配置即启用"的假象）
endlocal
