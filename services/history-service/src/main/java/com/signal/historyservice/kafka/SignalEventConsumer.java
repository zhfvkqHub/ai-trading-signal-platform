package com.signal.historyservice.kafka;

import com.signal.historyservice.entity.SignalEventEntity;
import com.signal.historyservice.model.SignalDetectedEvent;
import com.signal.historyservice.model.SignalRejectedEvent;
import com.signal.historyservice.repository.SignalEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalEventConsumer {

    private final SignalEventRepository signalEventRepository;

    @KafkaListener(topics = "${kafka.topics.signal-detected}", groupId = "${spring.kafka.consumer.group-id}")
    public void onSignalDetected(SignalDetectedEvent event) {
        try {
            SignalEventEntity entity = SignalEventEntity.builder()
                    .stockCode(event.stockCode())
                    .stockName(event.stockName())
                    .signalTypes(event.signalTypes().stream()
                            .map(Enum::name)
                            .collect(Collectors.joining(",")))
                    .score(event.score())
                    .status("DETECTED")
                    .reasons(event.reasons() != null
                            ? "[\"" + String.join("\",\"", event.reasons()) + "\"]"
                            : null)
                    .eventAt(event.detectedAt())
                    .traceId(event.traceId())
                    .build();

            signalEventRepository.save(entity);
            log.debug("시그널 감지 이벤트 저장 [stockCode={}, score={}]", event.stockCode(), event.score());
        } catch (Exception e) {
            log.error("시그널 감지 이벤트 저장 실패 [stockCode={}]", event.stockCode(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "${kafka.topics.signal-rejected}", groupId = "${spring.kafka.consumer.group-id}")
    public void onSignalRejected(SignalRejectedEvent event) {
        try {
            SignalEventEntity entity = SignalEventEntity.builder()
                    .stockCode(event.stockCode())
                    .stockName(event.stockName())
                    .signalTypes(event.signalTypes().stream()
                            .map(Enum::name)
                            .collect(Collectors.joining(",")))
                    .score(event.score())
                    .status("REJECTED")
                    .rejectionReason(event.rejectionReason())
                    .eventAt(event.rejectedAt())
                    .traceId(event.traceId())
                    .build();

            signalEventRepository.save(entity);
            log.debug("시그널 거부 이벤트 저장 [stockCode={}, reason={}]",
                    event.stockCode(), event.rejectionReason());
        } catch (Exception e) {
            log.error("시그널 거부 이벤트 저장 실패 [stockCode={}]", event.stockCode(), e);
            throw e;
        }
    }
}
