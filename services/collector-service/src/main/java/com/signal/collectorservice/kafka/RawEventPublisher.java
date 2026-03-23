package com.signal.collectorservice.kafka;

import com.signal.collectorservice.config.KafkaTopicProperties;
import com.signal.collectorservice.model.raw.RawAfterHoursEvent;
import com.signal.collectorservice.model.raw.RawMarketEvent;
import com.signal.collectorservice.model.raw.RawNewsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    public void publishMarketEvent(String stockCode, RawMarketEvent event) {
        publish(topicProperties.getRawMarket(), stockCode, event);
    }

    public void publishAfterHoursEvent(String stockCode, RawAfterHoursEvent event) {
        publish(topicProperties.getRawAfterHours(), stockCode, event);
    }

    public void publishNewsEvent(String receiptNo, RawNewsEvent event) {
        publish(topicProperties.getRawNews(), receiptNo, event);
    }

    private void publish(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);

        String traceId = MDC.get("traceId");
        if (traceId != null) {
            record.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka 발행 실패 [topic={}, key={}]", topic, key, ex);
            } else {
                log.debug("Kafka 발행 성공 [topic={}, key={}, offset={}]", topic, key, result.getRecordMetadata().offset());
            }
        });
    }
}
