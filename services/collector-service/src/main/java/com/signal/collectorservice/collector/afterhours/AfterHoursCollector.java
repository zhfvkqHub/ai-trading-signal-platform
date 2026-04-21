package com.signal.collectorservice.collector.afterhours;

import com.signal.collectorservice.client.kis.afterhours.KisAfterHoursFeignClient;
import com.signal.collectorservice.client.kis.afterhours.KisAfterHoursOrderBookResponse;
import com.signal.collectorservice.client.kis.afterhours.KisAfterHoursResponse;
import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.kafka.RawEventPublisher;
import com.signal.collectorservice.model.DataSource;
import com.signal.collectorservice.model.TradingSession;
import com.signal.collectorservice.model.raw.RawAfterHoursEvent;
import com.signal.collectorservice.schedule.TradingSessionManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AfterHoursCollector {

    private final KisAfterHoursFeignClient afterHoursClient;
    private final TradingSessionManager sessionManager;
    private final CollectorProperties collectorProperties;
    private final RawEventPublisher eventPublisher;
    private final RateLimiter kisRateLimiter;
    private final CircuitBreaker kisCircuitBreaker;
    @Qualifier("kisExecutor")
    private final ExecutorService kisExecutor;

    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 매일 장 종료 후 정해진 시간마다 KIS 시간외 시세 조회 API를 호출하여 시간외 시세를 수집한다.
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
            log.info("시간외 시세 수집 시작 [종목수={}, cbState={}]", stockCodes.size(), kisCircuitBreaker.getState());

            List<CompletableFuture<Void>> futures = stockCodes.stream()
                    .map(code -> CompletableFuture.runAsync(() -> collectSingle(code, traceId), kisExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("시간외 시세 수집 완료");
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private void collectSingle(String stockCode, String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
        if (!kisCircuitBreaker.tryAcquirePermission()) {
            log.debug("KIS Circuit Breaker OPEN — 요청 차단됨 [stockCode={}]", stockCode);
            MDC.remove(TRACE_ID_KEY);
            return;
        }
        long startNano = System.nanoTime();
        try {
            kisRateLimiter.acquirePermission();
            KisAfterHoursResponse response = afterHoursClient.fetchAfterHoursPrice(
                    "FHPST02300000", "J", stockCode);

            if (!"0".equals(response.returnCode())) {
                log.warn("KIS 시간외 조회 실패 [stockCode={}, msg={}]", stockCode, response.message());
                kisCircuitBreaker.onSuccess(System.nanoTime() - startNano, java.util.concurrent.TimeUnit.NANOSECONDS);
                return;
            }

            KisAfterHoursResponse.Output output = response.output();
            if (output == null) {
                log.debug("KIS 시간외 데이터 없음 [stockCode={}]", stockCode);
                kisCircuitBreaker.onSuccess(System.nanoTime() - startNano, java.util.concurrent.TimeUnit.NANOSECONDS);
                return;
            }

            // 시간외 호가 잔량 조회 (매수/매도 물량)
            BigDecimal buyOrderVolume = BigDecimal.ZERO;
            BigDecimal sellOrderVolume = BigDecimal.ZERO;
            try {
                kisRateLimiter.acquirePermission();
                KisAfterHoursOrderBookResponse orderBookResponse =
                        afterHoursClient.fetchAfterHoursOrderBook("FHPST02310000", "J", stockCode);
                if ("0".equals(orderBookResponse.returnCode()) && orderBookResponse.output1() != null) {
                    String bidStr = orderBookResponse.output1().totalBidVolume();
                    String askStr = orderBookResponse.output1().totalAskVolume();
                    if (bidStr != null && !bidStr.isBlank()) {
                        buyOrderVolume = new BigDecimal(bidStr);
                    }
                    if (askStr != null && !askStr.isBlank()) {
                        sellOrderVolume = new BigDecimal(askStr);
                    }
                }
            } catch (Exception e) {
                log.warn("시간외 호가 잔량 조회 실패 - 시세만 발행 [stockCode={}]", stockCode, e);
            }

            String stockName = collectorProperties.getStockName(stockCode);
            RawAfterHoursEvent event = new RawAfterHoursEvent(
                    stockCode,
                    stockName,
                    new BigDecimal(output.afterHoursPrice()),
                    new BigDecimal(output.afterHoursVolume()),
                    buyOrderVolume,
                    sellOrderVolume,
                    new BigDecimal(output.prevClosePrice()),
                    Instant.now(),
                    DataSource.KIS,
                    traceId
            );

            eventPublisher.publishAfterHoursEvent(stockCode, event);
            kisCircuitBreaker.onSuccess(System.nanoTime() - startNano, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            kisCircuitBreaker.onError(System.nanoTime() - startNano, java.util.concurrent.TimeUnit.NANOSECONDS, e);
            log.error("시간외 시세 수집 실패 [stockCode={}]", stockCode, e);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
