#!/bin/sh
set -e

sh cleanup-infra.sh
docker-compose -f docker-compose.yml down --remove-orphans
docker-compose -f docker-compose.yml up -d mysql mongo cassandra cassandra-init kafka

wait_for_port() {
  host="$1"
  port="$2"
  name="$3"
  for i in $(seq 1 60); do
    if nc -z "$host" "$port" >/dev/null 2>&1; then
      echo "$name is ready on $host:$port"
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for $name on $host:$port" >&2
  exit 1
}

wait_for_port localhost 3307 mysql
wait_for_port localhost 27017 mongo
wait_for_port localhost 9042 cassandra
wait_for_port localhost 29092 kafka

sh configure-kafka-topic.sh
