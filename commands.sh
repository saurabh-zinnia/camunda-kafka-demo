# Create a topic
./kafka-cli.sh --create --topic quickstart-events --partitions 1 --replication-factor 1

# List topics
./kafka-cli.sh --list

# Describe a topic
./kafka-cli.sh --describe --topic quickstart-events

# Produce a message
docker run --rm -it --network host \
  confluentinc/cp-kafka:7.4.0 \
  kafka-console-producer --bootstrap-server localhost:9092 --topic quickstart-events

# Consume messages
docker run --rm -it --network host \
  confluentinc/cp-kafka:7.4.0 \
  kafka-console-consumer --bootstrap-server localhost:9092 --topic intermediate-message-topic --from-beginning
