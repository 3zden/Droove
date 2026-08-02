/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // The dev proxy is a stand-in for the api-gateway that doesn't exist yet (M5).
  // Everything the browser calls is same-origin on :5173, so there is no CORS to
  // configure and no @CrossOrigin annotations to add to (and later remove from)
  // production Java code. It also rehearses exactly what the gateway will do:
  // one origin in, prefix routing out, prefix stripped before the service sees it.
  //
  // When the real gateway lands, delete this block and point the VITE_*_API_URL
  // variables at :8080. No frontend code changes.
  server: {
    proxy: {
      // user-service sets server.servlet.context-path=/api/users itself, so it
      // still expects to see the prefix. The others are unprefixed by design.
      '/api/users': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/trips': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/api/pricing': {
        target: 'http://localhost:8104',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/pricing/, ''),
      },
      '/api/routing': {
        target: 'http://localhost:8107',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/routing/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
  },
})
