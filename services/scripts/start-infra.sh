#!/bin/sh
set -e

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SERVICES_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$SERVICES_DIR/docker-compose.yml"
COMPOSE_HTTP_TIMEOUT="${COMPOSE_HTTP_TIMEOUT:-180}"
export COMPOSE_HTTP_TIMEOUT

sh "$SCRIPT_DIR/cleanup-infra.sh"
echo "Skipping compose down; cleanup script already removed stale containers."
echo "Starting core infrastructure containers..."
for service in mysql mongo elasticsearch cassandra kafka; do
  echo "Starting $service..."
  docker-compose -f "$COMPOSE_FILE" up -d "$service"
done
echo "Starting cassandra-init..."
docker-compose -f "$COMPOSE_FILE" up -d cassandra-init

wait_for_port() {
  host="$1"
  port="$2"
  name="$3"
  for i in $(seq 1 60); do
    if nc -z "$host" "$port" >/dev/null 2>&1; then
      echo "$name is ready on $host:$port"
      return 0
    fi
    echo "Waiting for $name on $host:$port ($i/60)..."
    sleep 2
  done
  echo "Timed out waiting for $name on $host:$port" >&2
  exit 1
}

wait_for_port localhost 3307 mysql
wait_for_port localhost 27017 mongo
wait_for_port localhost 9200 elasticsearch
wait_for_port localhost 9042 cassandra
wait_for_port localhost 29092 kafka

for i in $(seq 1 60); do
  health=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' shopping-kafka 2>/dev/null || true)
  if [ "$health" = "healthy" ]; then
    echo "kafka admin is ready"
    break
  fi
  echo "Waiting for kafka health check ($i/60)..."
  sleep 2
  if [ "$i" -eq 60 ]; then
    echo "Timed out waiting for kafka admin to become ready" >&2
    exit 1
  fi
done

for i in $(seq 1 45); do
  if docker exec shopping-cassandra cqlsh -e "DESCRIBE KEYSPACE online_shopping_order" >/dev/null 2>&1; then
    echo "cassandra keyspace is ready"
    break
  fi
  echo "Waiting for Cassandra keyspace initialization ($i/45)..."
  sleep 2
  if [ "$i" -eq 45 ]; then
    echo "Timed out waiting for Cassandra keyspace initialization" >&2
    exit 1
  fi
done

echo "Configuring Kafka topic..."
sh "$SCRIPT_DIR/configure-kafka-topic.sh"
