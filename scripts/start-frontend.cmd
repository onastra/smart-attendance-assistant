@echo off
REM ============================================================
REM  Lianzhi Attendance - frontend dev server launcher
REM ------------------------------------------------------------
REM  用法：scripts\start-frontend.cmd
REM  等价于 cd web && npm run dev，额外做了：
REM    - 校验 node_modules 已安装（缺失时自动 npm install）
REM    - 后端未启动时给出明确提示（避免误以为前端坏了）
REM  代理目标默认 http://localhost:8080（见 web/vite.config.js）。
REM ============================================================

setlocal
set "WEB_DIR=%~dp0..\web"

if not exist "%WEB_DIR%\package.json" (
  echo [ERROR] 未找到前端目录: %WEB_DIR%
  exit /b 1
)

if not exist "%WEB_DIR%\node_modules" (
  echo [INFO ] 首次运行，安装依赖中...
  pushd "%WEB_DIR%"
  call npm install
  popd
)

netstat -ano | findstr ":8080" | findstr LISTENING >nul
if errorlevel 1 (
  echo [WARN ] 后端 8080 端口没有服务在监听！
  echo         请先运行 scripts\start-backend.cmd，否则登录会报错。
  echo.
)

echo [INFO ] 启动前端开发服务 http://localhost:5173 ...
pushd "%WEB_DIR%"
call npm run dev
popd

endlocal
