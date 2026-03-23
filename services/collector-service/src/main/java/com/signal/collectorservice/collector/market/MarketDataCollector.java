package com.signal.collectorservice.collector.market;

import com.signal.collectorservice.client.kis.market.KisMarketFeignClient;
import com.signal.collectorservice.client.kis.market.KisMarketResponse;
import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.kafka.RawEventPublisher;
import com.signal.collectorservice.model.DataSource;
import com.signal.collectorservice.model.TradingSession;
import com.signal.collectorservice.model.raw.RawMarketEvent;
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
public class MarketDataCollector {

    private final KisMarketFeignClient marketClient;
    private final TradingSessionManager sessionManager;
    private final CollectorProperties collectorProperties;
    private final RawEventPublisher eventPublisher;

    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 매일 장중 정해진 시간마다 KIS 시세 조회 API를 호출하여 실시간 시세를 수집한다.
     - 수집 대상: 모니터링 대상 종목 전체
     - 수집 내용: 현재가, 시가, 고가, 저가, 전일 종가, 거래량, 거래대금 등
     - 발행: 수집된 시세 정보를 Kafka로 발행하여 후속 처리 시스템에서 소비하도록 함
     */
    @Scheduled(cron = "${collector.schedule.market.cron}")
    public void collect() {
        if (!sessionManager.isBusinessDay() || !sessionManager.isSessionActive(TradingSession.REGULAR)) {
            return;
        }

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);

        try {
            List<String> stockCodes = collectorProperties.getStockCodes();
            log.info("장중 시세 수집 시작 [종목수={}]", stockCodes.size());

            List<CompletableFuture<Void>> futures = stockCodes.stream()
                    .map(code -> CompletableFuture.runAsync(() -> collectSingle(code, traceId)))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("장중 시세 수집 완료");
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private void collectSingle(String stockCode, String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
        try {
            KisMarketResponse response = marketClient.fetchCurrentPrice(
                    "FHKST01010100", "J", stockCode);

            if (!"0".equals(response.returnCode())) {
                log.warn("KIS 시세 조회 실패 [stockCode={}, msg={}]", stockCode, response.message());
                return;
            }

            KisMarketResponse.Output output = response.output();
            RawMarketEvent event = new RawMarketEvent(
                    stockCode,
                    output.stockName(),
                    new BigDecimal(output.currentPrice()),
                    new BigDecimal(output.openPrice()),
                    new BigDecimal(output.highPrice()),
                    new BigDecimal(output.lowPrice()),
                    new BigDecimal(output.prevClosePrice()),
                    new BigDecimal(output.accumulatedVolume()),
                    new BigDecimal(output.tradingValue()),
                    Instant.now(),
                    DataSource.KIS,
                    traceId
            );

            eventPublisher.publishMarketEvent(stockCode, event);
        } catch (Exception e) {
            log.error("장중 시세 수집 실패 [stockCode={}]", stockCode, e);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
