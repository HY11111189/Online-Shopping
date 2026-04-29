#!/bin/sh
set -e

COMPOSE_FILE="docker-compose.yml"
TOPIC_SCRIPT="./configure-kafka-topic.sh"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if docker ps -a --format '{{.Names}}' | grep -qx 'shopping-kafka'; then
  docker rm -f shopping-kafka >/dev/null 2>&1 || true
fi

if docker volume ls --format '{{.Name}}' | grep -qx 'services_kafka_data'; then
  docker volume rm services_kafka_data >/dev/null 2>&1 || true
fi

docker-compose -f "$COMPOSE_FILE" up -d kafka

for i in $(seq 1 60); do
  if nc -z localhost 29092 >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! nc -z localhost 29092 >/dev/null 2>&1; then
  echo "Timed out waiting for kafka on localhost:29092" >&2
  exit 1
fi

sh "$TOPIC_SCRIPT"

echo "Kafka has been reset. Restart payment-service before running another checkout race."
