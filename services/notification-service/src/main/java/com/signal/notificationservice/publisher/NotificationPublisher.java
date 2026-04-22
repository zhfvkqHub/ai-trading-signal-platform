package com.signal.notificationservice.publisher;

import com.signal.notificationservice.config.properties.KafkaTopicProperties;
import com.signal.notificationservice.model.NotificationDispatchedEvent;
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
public class NotificationPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    /**
     * 알림 발송 이벤트를 발행한다 (SENT / SUPPRESSED 모두 포함). 실패 시 로그만 남기고 무시한다.
     */
    public void publishDispatched(NotificationDispatchedEvent event) {
        try {
            String topic = topicProperties.getNotificationDispatched();
            ProducerRecord<String, Object> record = new ProducerRecord<>(topic, event.stockCode(), event);

            String traceId = MDC.get("traceId");
            if (traceId != null) {
                record.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
            }

            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("알림 이벤트 발행 [channel={}, stockCode={}, status={}]",
                    event.channel(), event.stockCode(), event.status());
        } catch (Exception e) {
            log.warn("알림 이벤트 발행 실패 (무시) [channel={}, stockCode={}]",
                    event.channel(), event.stockCode(), e);
        }
    }
}
