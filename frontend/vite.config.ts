import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Config de desarrollo y build (FR-008): el proxy reenvía /api al backend
// (sin rewrite) para mantener same-origin desde el navegador (A-001).
// Bloque `test` para Vitest (jsdom + setup compartido + globals).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
});
