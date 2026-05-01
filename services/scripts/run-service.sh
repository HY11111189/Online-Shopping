#!/bin/sh
set -e

if [ $# -lt 1 ]; then
  echo "Usage: sh scripts/run-service.sh <service-dir> [maven-args...]" >&2
  exit 1
fi

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVICES_DIR="$(dirname "$SCRIPT_DIR")"
SERVICE_DIR="$SERVICES_DIR/$1"
shift

if [ ! -d "$SERVICE_DIR" ]; then
  echo "Unknown service directory: $SERVICE_DIR" >&2
  exit 1
fi

if [ -f "$SERVICES_DIR/.env.local" ]; then
  set -a
  . "$SERVICES_DIR/.env.local"
  set +a
fi

cd "$SERVICE_DIR"
mvn spring-boot:run "$@"
