package com.signal.notificationservice.listener;

import com.signal.notificationservice.model.SignalDetectedEvent;
import com.signal.notificationservice.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalDetectedListener {

    private final NotificationDispatchService dispatchService;
    private static final String TRACE_ID_KEY = "traceId";

    @KafkaListener(
            topics = "${kafka.topics.signal-detected}",
            groupId = "notification-service",
            properties = {
                    "spring.json.value.default.type=com.signal.notificationservice.model.SignalDetectedEvent"
            }
    )
    public void onSignalDetected(ConsumerRecord<String, SignalDetectedEvent> kafkaRecord) {
        String traceId = extractTraceId(kafkaRecord);
        MDC.put(TRACE_ID_KEY, traceId);

        try {
            SignalDetectedEvent event = kafkaRecord.value();
            log.info("신호 이벤트 수신 [stockCode={}, score={}, types={}]",
                    event.stockCode(), event.score(), event.signalTypes());
            dispatchService.dispatch(event);
        } catch (Exception e) {
            log.error("신호 이벤트 처리 실패 [key={}]", kafkaRecord.key(), e);
            throw e; // KafkaConsumerConfig의 DLQ 핸들러로 전달
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String extractTraceId(ConsumerRecord<String, ?> record) {
        var header = record.headers()
                .lastHeader(TRACE_ID_KEY);
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }
}
