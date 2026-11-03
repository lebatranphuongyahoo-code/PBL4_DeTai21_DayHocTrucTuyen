import { defineConfig } from 'vite';
export default defineConfig({
  server: { port: 5173, strictPort: true, proxy: { '/api': 'http://127.0.0.1:8081', '/ws': { target: 'ws://127.0.0.1:8081', ws: true } } },
  preview: { port: 4173 },
});
