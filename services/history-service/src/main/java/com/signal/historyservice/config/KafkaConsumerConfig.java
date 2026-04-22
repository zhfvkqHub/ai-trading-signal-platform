package com.signal.historyservice.config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        );

        var errorHandler = buildErrorHandler(producerProps);

        log.info("Kafka DLQ 에러 핸들러 초기화 완료 (history-service)");
        return errorHandler;
    }

    private static @NonNull DefaultErrorHandler buildErrorHandler(
            Map<String, Object> producerProps) {
        KafkaOperations<String, Object> dlqTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));

        var recoverer = new DeadLetterPublishingRecoverer(dlqTemplate,
                (record, ex) -> new TopicPartition(
                        record.topic() + ".dlq", record.partition()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(1000L, 2));
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        return errorHandler;
    }
}
