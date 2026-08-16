import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  // Vite dev proxy: same-origin élmény dev módban
  // A backend a 8080-as porton fut, a Vite a 5173-on
  // A /api/* kéréseket átirányítjuk a backend-re
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Cookie átengedése (refresh token cookie SameSite=Strict problémát okozhat dev-ben,
        // ezért a Vite proxy forward-olja a cookie-kat)
        cookieDomainRewrite: 'localhost',
      },
    },
  },

  // Path alias @/ → src/
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  // Build konfiguráció
  build: {
    outDir: 'dist',
    sourcemap: true,
    // Chunk-ok kisebbek legyenek a jobb caching érdekében
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'query-vendor': ['@tanstack/react-query'],
          'ui-vendor': ['@radix-ui/react-dialog', '@radix-ui/react-dropdown-menu', 'lucide-react'],
        },
      },
    },
  },

  // Dev server optimalizáció
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query'],
  },
})