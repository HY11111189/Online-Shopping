#!/bin/sh
set -e

# Install the shared module once so service-local spring-boot:run can resolve it.
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVICES_DIR="$(dirname "$SCRIPT_DIR")"
if [ -f "$SERVICES_DIR/.env.local" ]; then
  set -a
  . "$SERVICES_DIR/.env.local"
  set +a
fi

mvn -f pom.xml -pl shared-lib -am install -DskipTests
