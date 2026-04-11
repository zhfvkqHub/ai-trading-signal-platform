package com.signal.signalservice.listener;

import com.signal.signalservice.consumer.dto.RawAfterHoursEvent;
import com.signal.signalservice.pipeline.SignalPipeline;
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
public class AfterHoursEventListener {

    private final SignalPipeline pipeline;
    private static final String TRACE_ID_KEY = "traceId";

    @KafkaListener(
            topics = "${kafka.topics.raw-after-hours}",
            groupId = "signal-service",
            properties = {"spring.json.value.default.type=com.signal.signalservice.consumer.dto.RawAfterHoursEvent"}
    )
    public void onMessage(ConsumerRecord<String, RawAfterHoursEvent> kafkaRecord) {
        String traceId = extractTraceId(kafkaRecord);
        MDC.put(TRACE_ID_KEY, traceId);

        try {
            RawAfterHoursEvent event = kafkaRecord.value();
            log.debug("시간외 데이터 수신 [stockCode={}, price={}]",
                    event.stockCode(), event.afterHoursPrice());
            pipeline.processAfterHoursEvent(event);
        } catch (Exception e) {
            log.error("시간외 데이터 처리 실패 [key={}]", kafkaRecord.key(), e);
            throw e;
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String extractTraceId(ConsumerRecord<String, ?> kafkaRecord) {
        var header = kafkaRecord.headers().lastHeader(TRACE_ID_KEY);
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return UUID.randomUUID()
                .toString();
    }
}
