#!/bin/bash
# ZenoLabs — Backend (macOS)
# Boots labs-backend with the 'local' Spring profile, which loads
# application-local.properties (Supabase creds + dev SSO bypass).

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"

if lsof -ti:8086 > /dev/null 2>&1; then
  echo "🔪  Killing existing process on port 8086..."
  lsof -ti:8086 | xargs kill -9 2>/dev/null
  sleep 1
fi

echo ""
echo " ZenoLabs Backend"
echo " Spring Boot 4 / Java 21 / profile: local"
echo " http://localhost:8086"
echo " Auth: validates JWT from Authorization: Bearer (set by labs-frontend"
echo "       .env.local VITE_MOCK_JWT) or sso_token cookie."
echo " Press Ctrl+C to stop."
echo ""

./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Xmx512m"
