import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const target = loadEnv(mode, '.', 'VITE_').VITE_BLOG_API_PROXY || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': target,
        '^/u/[^/]+/rss\\.xml(?:$|\\?)': target,
      },
    },
  }
})
