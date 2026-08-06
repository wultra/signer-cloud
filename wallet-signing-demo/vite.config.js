import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const scaProxy = {
  target: 'http://localhost:8090/',
  changeOrigin: true,
  secure: false,
  rewrite: (path) => path.replace(/^\/sca/, ''),
}

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/sca': scaProxy,
    },
  },
  preview: {
    proxy: {
      '/sca': scaProxy,
    },
  },
})
