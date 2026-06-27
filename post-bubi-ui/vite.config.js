import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd())
  const isBuild = command === 'build'

  return {
    base: env.VITE_PUBLIC_PATH || '/',
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT || 5173),
      open: false,
      proxy: {
        '/api': {
          target: env.VITE_API_TARGET || 'http://localhost:18080',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: './dist/post-bubi',
      emptyOutDir: true,
      reportCompressedSize: false,
      sourcemap: !isBuild,
      chunkSizeWarningLimit: 1024,
    },
  }
})
