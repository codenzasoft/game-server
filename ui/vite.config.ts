import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  server: {
    host: true,
    port: 8085,
    open: true,
    proxy: {
      "/games-connect": {
        target: "ws://localhost:8082",
        ws: true,
      },
      "/": {
        target: "http://localhost:8082",
        bypass(req) {
          const url = req.url || "";
          // Let Vite handle its own internal paths (HMR, module transforms, etc.)
          if (
            url.startsWith("/@") ||
            url.startsWith("/src/") ||
            url.startsWith("/node_modules/")
          ) {
            return url;
          }
          // Serve the game page from Vite for HMR
          if (
            req.method === "GET" &&
            /^\/games\/xo\/[0-9a-f-]{36}/.test(url)
          ) {
            return "/index.html";
          }
        },
      },
    },
  },
});
