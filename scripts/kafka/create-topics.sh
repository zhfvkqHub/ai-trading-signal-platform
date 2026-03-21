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

for topic in "${topics[@]}"
do
  echo "Creating topic: $topic"
  docker exec kafka kafka-topics.sh \
    --create \
    --if-not-exists \
    --bootstrap-server $BROKER \
    --replication-factor 1 \
    --partitions 3 \
    --topic $topic
done
