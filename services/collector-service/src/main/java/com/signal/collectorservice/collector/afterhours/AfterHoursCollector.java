package com.signal.collectorservice.collector.afterhours;

import com.signal.collectorservice.client.kis.afterhours.KisAfterHoursFeignClient;
import com.signal.collectorservice.client.kis.afterhours.KisAfterHoursResponse;
import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.kafka.RawEventPublisher;
import com.signal.collectorservice.model.DataSource;
import com.signal.collectorservice.model.TradingSession;
import com.signal.collectorservice.model.raw.RawAfterHoursEvent;
import com.signal.collectorservice.schedule.TradingSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AfterHoursCollector {

    private final KisAfterHoursFeignClient afterHoursClient;
    private final TradingSessionManager sessionManager;
    private final CollectorProperties collectorProperties;
    private final RawEventPublisher eventPublisher;
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 매일 장 종료 후 정해진 시간마다 KIS 시간외 시세 조회 API를 호출하여 시간외 시세를 수집한다.
     - 수집 대상: 모니터링 대상 종목 전체
     - 수집 내용: 시간외 현재가, 시간외 거래량, 전일 종가 등
     - 발행: 수집된 시간외 시세 정보를 Kafka로 발행하여 후속 처리 시스템에서 소비하도록 함
     */
    @Scheduled(cron = "${collector.schedule.after-hours.cron}")
    public void collect() {
        if (!sessionManager.isBusinessDay() || !sessionManager.isSessionActive(TradingSession.AFTER_HOURS)) {
            return;
        }

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);

        try {
            List<String> stockCodes = collectorProperties.getStockCodes();
            log.info("시간외 시세 수집 시작 [종목수={}]", stockCodes.size());

            List<CompletableFuture<Void>> futures = stockCodes.stream()
                    .map(code -> CompletableFuture.runAsync(() -> collectSingle(code, traceId)))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("시간외 시세 수집 완료");
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private void collectSingle(String stockCode, String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
        try {
            KisAfterHoursResponse response = afterHoursClient.fetchAfterHoursPrice(
                    "FHPST02300000", "J", stockCode);

            if (!"0".equals(response.returnCode())) {
                log.warn("KIS 시간외 조회 실패 [stockCode={}, msg={}]", stockCode, response.message());
                return;
            }

            KisAfterHoursResponse.Output output = response.output();
            if (output == null) {
                log.debug("KIS 시간외 데이터 없음 [stockCode={}]", stockCode);
                return;
            }
            String stockName = output.stockName();
            RawAfterHoursEvent event = new RawAfterHoursEvent(
                    stockCode,
                    stockName,
                    new BigDecimal(output.afterHoursPrice()),
                    new BigDecimal(output.afterHoursVolume()),
                    BigDecimal.ZERO,  // 매수 대기 물량 (해당 필드 없음)
                    BigDecimal.ZERO,  // 매도 대기 물량 (해당 필드 없음)
                    new BigDecimal(output.prevClosePrice()),
                    Instant.now(),
                    DataSource.KIS,
                    traceId
            );

            eventPublisher.publishAfterHoursEvent(stockCode, event);
        } catch (Exception e) {
            log.error("시간외 시세 수집 실패 [stockCode={}]", stockCode, e);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
