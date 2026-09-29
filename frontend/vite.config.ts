import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En développement, /api est redirigé vers le backend Spring Boot (port 8081) :
// même origine pour le navigateur, donc cookies de session et CSRF sans configuration CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: false },
    },
  },
});
