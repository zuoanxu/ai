import vue from '@vitejs/plugin-vue'
  import { defineConfig } from 'vite'

  // https://vite.dev/config/
  export default defineConfig({
    plugins: [vue()],
    server: {
      proxy: {
        // 把 /assistant 开头的请求，转发到后端 8081，解决跨域
        '/assistant': {
          target: 'http://localhost:8081',
          changeOrigin: true,
        },
      },
    },
  })