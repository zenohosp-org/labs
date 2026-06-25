#!/bin/bash
# ZenoLabs — Frontend launcher (macOS)
# Runs the Vite dev server on port 5175 with VITE_API_BASE_URL pointed at
# the local backend so /api/* calls hit http://localhost:8086.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/labs-frontend"

if [[ ! -d node_modules ]]; then
  echo "📦  Installing dependencies (first run)..."
  npm install
fi

export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:8086}"

if [[ ! -f .env.local ]]; then
  echo "⚠️   labs-frontend/.env.local not found."
  echo "    Copy .env.example to .env.local and fill VITE_MOCK_JWT (real signed token)"
  echo "    so the dev mock-auth bypass works. The token is gitignored."
  echo ""
fi

echo " ZenoLabs Frontend"
echo " Vite dev server"
echo " api base URL : $VITE_API_BASE_URL"
echo " open         : http://localhost:5175"
echo " auth mode    : VITE_DEV_MOCK_AUTH (Bearer header from .env.local)"
echo ""

exec npm run dev -- --port 5175 --host
