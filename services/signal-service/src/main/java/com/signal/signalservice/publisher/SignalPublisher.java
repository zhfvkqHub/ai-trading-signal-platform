package com.signal.signalservice.publisher;

import com.signal.signalservice.config.properties.KafkaTopicProperties;
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
public class SignalPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    public void publishDetected(SignalDetectedEvent event) {
        publish(topicProperties.getSignalDetected(), event.stockCode(), event);
        log.info("신호 발행 [stockCode={}, score={}, types={}]",
                event.stockCode(), event.score(), event.signalTypes());
    }

    public void publishRejected(SignalRejectedEvent event) {
        publish(topicProperties.getSignalRejected(), event.stockCode(), event);
        log.debug("거부 신호 발행 [stockCode={}, reason={}]",
                event.stockCode(), event.rejectionReason());
    }

    private void publish(String topic, String key, Object event) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, key, event);

        String traceId = MDC.get("traceId");
        if (traceId != null) {
            producerRecord.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(producerRecord).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka 발행 실패 [topic={}, key={}]", topic, key, ex);
            } else {
                log.debug("Kafka 발행 성공 [topic={}, key={}, offset={}]",
                        topic, key, result.getRecordMetadata().offset());
            }
        });
    }
}
