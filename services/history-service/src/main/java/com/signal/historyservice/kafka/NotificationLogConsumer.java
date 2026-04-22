package com.signal.historyservice.kafka;

import com.signal.historyservice.entity.NotificationLogEntity;
import com.signal.historyservice.model.NotificationDispatchedEvent;
import com.signal.historyservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationLogConsumer {

    private final NotificationLogRepository notificationLogRepository;

    @KafkaListener(topics = "${kafka.topics.notification-dispatched}", groupId = "${spring.kafka.consumer.group-id}")
    public void onNotificationDispatched(NotificationDispatchedEvent event) {
        try {
            NotificationLogEntity entity = NotificationLogEntity.builder()
                    .stockCode(event.stockCode())
                    .stockName(event.stockName())
                    .signalTypes(event.signalTypes().stream()
                            .map(Enum::name)
                            .collect(Collectors.joining(",")))
                    .score(event.score())
                    .channel(event.channel())
                    .status(event.status())
                    .suppressReason(event.suppressReason())
                    .dispatchedAt(event.dispatchedAt())
                    .traceId(event.traceId())
                    .build();

            notificationLogRepository.save(entity);
            log.debug("알림 발송 이벤트 저장 [channel={}, stockCode={}, status={}]",
                    event.channel(), event.stockCode(), event.status());
        } catch (Exception e) {
            log.error("알림 발송 이벤트 저장 실패 [channel={}, stockCode={}]",
                    event.channel(), event.stockCode(), e);
            throw e;
        }
    }
}
