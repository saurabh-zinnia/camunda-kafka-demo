package org.camunda.bpm.demo.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value(value = "${kafka.bootstrap-address}")
    private String bootstrapAddress;

    @Value(value = "${kafka.trusted-packages}")
    private String trustedPackage;

    @Bean
    public ProducerFactory<String, CamundaMessageDto> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapAddress);
        props.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackage);
        
        // Add reliability and compatibility configurations
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // Wait for leader acknowledgment
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CamundaMessageDto> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}