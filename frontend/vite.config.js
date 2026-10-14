import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 配置：开发时把 /api 代理到后端 8080，避免跨域问题
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 打包产物直接输出到后端静态资源目录，实现"一个 jar 同时提供页面与接口"的合并部署
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  }
})
