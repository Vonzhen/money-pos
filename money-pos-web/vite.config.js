import { defineConfig } from "vite";
import { resolve } from 'path';
import vue from "@vitejs/plugin-vue";
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'

export default defineConfig(async ({mode}) => ({
  // 前端页面的基础访问路径
  base: mode  === 'demo' ? '/money-pos-demo' : '/money-pos',
  envPrefix: ["VITE_"],
  server: {
    port: 1520,
    strictPort: true,
    // 🌟 架构规范：使用 /api 作为 API 专属网关前缀，彻底避开前端文件路由
    proxy: {
      '/api': {
        target: 'http://localhost:9101',
        changeOrigin: true,
        // 将前端发出的 /api/xxx 请求，在底层静默重写为后端的真实路径 /money-pos/xxx
        rewrite: (path) => path.replace(/^\/api/, '/money-pos')
      }
    }
  },

  plugins: [
    vue(),
    createSvgIconsPlugin({
      iconDirs: [resolve(__dirname, 'src/assets/icons')],
      symbolId: 'icon-[dir]-[name]'
    }),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],

  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },

  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: process.env.NODE_ENV === 'development',
    minify: 'esbuild',
  },
}))