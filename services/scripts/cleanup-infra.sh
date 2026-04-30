#!/bin/sh
set -e

docker rm -f \
  shopping-account-service \
  shopping-item-service \
  shopping-order-service \
  shopping-payment-service \
  shopping-gateway-service \
  shopping-mysql \
  shopping-mongo \
  shopping-elasticsearch \
  shopping-cassandra \
  shopping-cassandra-init \
  shopping-kafka \
  >/dev/null 2>&1 || true

docker volume rm \
  services_kafka_config \
  services_kafka_data \
  >/dev/null 2>&1 || true

docker network prune -f >/dev/null 2>&1 || true
