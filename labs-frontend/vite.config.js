import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";
import autoprefixer from "autoprefixer";

export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            "@": path.resolve("./src"),
        },
    },
    css: {
        postcss: {
            plugins: [autoprefixer()],
        },
    },
    server: {
        port: 5175,
        host: true,
        proxy: {
            "/api": {
                target: "http://localhost:8086",
                changeOrigin: true,
            },
            "/oauth2": {
                target: "http://localhost:8086",
                changeOrigin: true,
            },
            "/login/oauth2": {
                target: "http://localhost:8086",
                changeOrigin: true,
            },
        },
    },
});
