#!/bin/bash

BROKER=localhost:9092

topics=(
  raw.market
  raw.news
  raw.after-hours
  signal.detected
  signal.rejected
  trade.plan
)

# Wait for Kafka to be ready
echo "Waiting for Kafka..."
until docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BROKER --list >/dev/null 2>&1
do
  sleep 2
done

echo "Kafka is ready."

for topic in "${topics[@]}"
do
  echo "Creating topic: $topic"
  docker exec kafka /opt/kafka/bin/kafka-topics.sh \
    --create \
    --if-not-exists \
    --bootstrap-server $BROKER \
    --replication-factor 1 \
    --partitions 3 \
    --topic $topic
done
