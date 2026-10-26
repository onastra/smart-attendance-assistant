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
