#!/bin/sh
set -e

TOPIC_NAME="${SHOPPING_KAFKA_ORDER_PLACED_TOPIC:-shopping.order.placed.v1}"
PARTITIONS="${SHOPPING_KAFKA_ORDER_PLACED_PARTITIONS:-8}"
BROKER="127.0.0.1:9092"

if ! docker ps --format '{{.Names}}' | grep -qx 'shopping-kafka'; then
  echo "shopping-kafka container is not running" >&2
  exit 1
fi

for i in $(seq 1 60); do
  if docker exec shopping-kafka kafka-topics --bootstrap-server "$BROKER" --list >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker exec shopping-kafka kafka-topics --bootstrap-server "$BROKER" --list >/dev/null 2>&1; then
  echo "Timed out waiting for Kafka admin commands on $BROKER" >&2
  exit 1
fi

docker exec shopping-kafka kafka-topics --bootstrap-server "$BROKER" \
  --create --if-not-exists --topic "$TOPIC_NAME" --partitions "$PARTITIONS" --replication-factor 1 >/dev/null

CURRENT_PARTITIONS="$(docker exec shopping-kafka kafka-topics --bootstrap-server "$BROKER" --describe --topic "$TOPIC_NAME" \
  | awk -F'PartitionCount: ' 'NR==1 {split($2,a,"\t"); print a[1]}')"

if [ -n "$CURRENT_PARTITIONS" ] && [ "$CURRENT_PARTITIONS" -lt "$PARTITIONS" ]; then
  docker exec shopping-kafka kafka-topics --bootstrap-server "$BROKER" \
    --alter --topic "$TOPIC_NAME" --partitions "$PARTITIONS" >/dev/null
fi

echo "Kafka topic configured: $TOPIC_NAME ($PARTITIONS partitions)"
docker exec shopping-kafka kafka-topics --bootstrap-server "$BROKER" --describe --topic "$TOPIC_NAME"
