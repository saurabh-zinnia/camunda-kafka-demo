#!/bin/bash

docker run --rm -it --network host \
  confluentinc/cp-kafka:7.4.0 \
  kafka-topics --bootstrap-server localhost:9092 "$@"