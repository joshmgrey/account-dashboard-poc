import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// During development, requests to /api are proxied to the Spring Boot
// backend so the browser only ever talks to the Vite dev server origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
