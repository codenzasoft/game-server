import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  server: {
    cors: {
      origin: ["http://localhost:8085", "http://localhost:8081"],
      methods: ["GET", "POST"],
      allowedHeaders: ["Content-Type"],
      credentials: true,
    },
    host: true,
    port: 8085,
    open: true,
  },
});
