import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 监听所有网卡：手机与电脑在同一局域网时，可以用 http://<电脑IP>:5173/h5/checkin
    // 打开 H5 打卡页做真机演示（后端 8080 已监听 0.0.0.0）。
    // 注意：浏览器只在 HTTPS 或 localhost 下允许调用摄像头与定位，
    // 真机走局域网 HTTP 时这两项会被禁用，H5 页会自动降级到手动输入二维码原文。
    host: '0.0.0.0',
    // 开发期通过代理访问后端，避免浏览器跨域；生产环境由 Nginx 或同源部署处理。
    // 后端默认端口为 8080（见 server/lz-app/src/main/resources/application.yml 与 scripts/start-backend.cmd）。
    // 若后端改用其他端口，用环境变量覆盖：LZ_API_TARGET=http://localhost:8088
    proxy: {
      '/api': {
        target: process.env.LZ_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500
  }
})
