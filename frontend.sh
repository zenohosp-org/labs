#!/usr/bin/env bash
# Run labs-frontend (Vite dev server) locally on port 5175.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/labs-frontend"

if [[ ! -d node_modules ]]; then
  echo "📦 Installing dependencies (first run)..."
  npm install
fi

export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:8086}"

echo "🧪 Starting labs-frontend"
echo "    api base URL : $VITE_API_BASE_URL"
echo "    open         : http://localhost:5175/login"
echo

exec npm run dev -- --port 5175 --host
