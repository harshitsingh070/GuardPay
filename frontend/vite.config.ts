import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// GuardPay frontend — React + Vite. Dev/preview serve on :3000 so the
// docker-compose port mapping and README URLs stay unchanged.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: true,
    port: 3000,
    strictPort: true,
  },
  preview: {
    host: true,
    port: 3000,
  },
});
