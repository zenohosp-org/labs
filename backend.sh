#!/usr/bin/env bash
# Run labs-backend locally.
#
# - Uses local-friendly OAuth/SSO env vars by default so the app boots without
#   hitting api-directory.zenohosp.com. The SSO login button still redirects
#   the browser to the directory at zenohosp.com (which sets the shared
#   sso_token cookie on .zenohosp.com).
# - To use real prod directory endpoints, run with: USE_PROD_SSO=1 ./backend.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/labs-backend"

export PORT="${PORT:-8086}"
export FRONTEND_URL="${FRONTEND_URL:-http://localhost:5175}"
export SSO_COOKIE_SECURE="${SSO_COOKIE_SECURE:-false}"

if [[ "${USE_PROD_SSO:-0}" != "1" ]]; then
  # Local placeholders — don't blow up on boot even if directory backend is unreachable.
  export OAUTH_REDIRECT_URI="${OAUTH_REDIRECT_URI:-http://localhost:${PORT}/login/oauth2/code/directory}"
  export DIRECTORY_AUTH_URL="${DIRECTORY_AUTH_URL:-https://api-directory.zenohosp.com/oauth2/authorize}"
  export DIRECTORY_TOKEN_URL="${DIRECTORY_TOKEN_URL:-https://api-directory.zenohosp.com/oauth2/token}"
  export DIRECTORY_USER_INFO_URL="${DIRECTORY_USER_INFO_URL:-https://api-directory.zenohosp.com/api/user/me}"
fi

echo "🧪 Starting labs-backend on port $PORT"
echo "    frontend URL : $FRONTEND_URL"
echo "    auth uri     : $DIRECTORY_AUTH_URL"
echo

exec ./mvnw spring-boot:run "$@"
