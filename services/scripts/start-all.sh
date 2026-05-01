#!/bin/sh
set -e

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVICES_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_HTTP_TIMEOUT="${COMPOSE_HTTP_TIMEOUT:-180}"
export COMPOSE_HTTP_TIMEOUT

if [ -f "$SERVICES_DIR/.env.local" ]; then
  set -a
  . "$SERVICES_DIR/.env.local"
  set +a
fi

docker-compose -f "$SERVICES_DIR/docker-compose.yml" up --build
