package com.signal.signalservice.publisher;

import com.signal.signalservice.config.properties.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    /**
     * 감지된 신호를 발행한다.
     *
     * <p>[A15 수정] 동기 전송(타임아웃 5초)으로 Kafka 발행 실패 시 예외를 전파한다.
     * 리스너가 예외를 DLQ로 라우팅하므로 신호가 조용히 유실되지 않는다.
     */
    public void publishDetected(SignalDetectedEvent event) {
        publishSync(topicProperties.getSignalDetected(), event.stockCode(), event);
        log.info("신호 발행 [stockCode={}, score={}, types={}]",
                event.stockCode(), event.score(), event.signalTypes());
    }

    /**
     * 거부된 신호를 발행한다. 실패 시 로그만 남기고 무시한다 (운영 지표용이므로 유실 허용).
     */
    public void publishRejected(SignalRejectedEvent event) {
        try {
            publishSync(topicProperties.getSignalRejected(), event.stockCode(), event);
        } catch (Exception e) {
            log.warn("거부 신호 발행 실패 (무시) [stockCode={}, reason={}]",
                    event.stockCode(), event.rejectionReason(), e);
        }
        log.debug("거부 신호 발행 [stockCode={}, reason={}]",
                event.stockCode(), event.rejectionReason());
    }

    private void publishSync(String topic, String key, Object event) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, key, event);

        String traceId = MDC.get("traceId");
        if (traceId != null) {
            producerRecord.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
        }

        try {
            kafkaTemplate.send(producerRecord)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Kafka 발행 성공 [topic={}, key={}]", topic, key);
        } catch (Exception e) {
            log.error("Kafka 발행 실패 [topic={}, key={}]", topic, key, e);
            throw new RuntimeException("Kafka 발행 실패: " + topic, e);
        }
    }
}
