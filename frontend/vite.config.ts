import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";
import fs from "fs";

const LOG_FILE = path.resolve(__dirname, ".vite-requests.log");

function requestLogger(): Plugin {
  return {
    name: "request-logger",
    configureServer(server) {
      // Clear log file on server start
      fs.writeFileSync(LOG_FILE, "");

      server.middlewares.use((req, res, next) => {
        const now = new Date().toLocaleTimeString();
        const url = req.url ?? "/";

        // Handle navigation pings from the SPA
        if (url.startsWith("/__log")) {
          const params = new URLSearchParams(url.split("?")[1] || "");
          const nav = params.get("nav") || "unknown";
          const line = `[${now}] navigate: ${nav.replace("-to-", " -> ")}\n`;
          console.log(line.trimEnd());
          fs.appendFileSync(LOG_FILE, line);
          res.writeHead(204);
          res.end();
          return;
        }

        // Skip noisy internal requests
        if (!url.includes("__vite") && !url.includes("@vite") && !url.includes("node_modules")) {
          const method = req.method ?? "GET";
          const line = `[${now}] ${method} ${url}\n`;
          console.log(line.trimEnd());
          fs.appendFileSync(LOG_FILE, line);
        }
        next();
      });
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [requestLogger(), react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
