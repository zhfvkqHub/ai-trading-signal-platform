package com.signal.collectorservice.collector.market;

import com.signal.collectorservice.client.kis.market.KisMarketFeignClient;
import com.signal.collectorservice.client.kis.market.KisMarketResponse;
import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.kafka.RawEventPublisher;
import com.signal.collectorservice.model.DataSource;
import com.signal.collectorservice.model.TradingSession;
import com.signal.collectorservice.model.raw.RawMarketEvent;
import com.signal.collectorservice.schedule.TradingSessionManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
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
public class MarketDataCollector {

    private final KisMarketFeignClient marketClient;
    private final TradingSessionManager sessionManager;
    private final CollectorProperties collectorProperties;
    private final RawEventPublisher eventPublisher;
    private final RateLimiter kisRateLimiter;
    private final RetryTemplate kisRetryTemplate;
    private final CircuitBreaker kisCircuitBreaker;
    @Qualifier("kisExecutor")
    private final ExecutorService kisExecutor;

    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 매일 장중 정해진 시간마다 KIS 시세 조회 API를 호출하여 실시간 시세를 수집한다.
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
            CircuitBreaker.State cbState = kisCircuitBreaker.getState();
            log.info("장중 시세 수집 시작 [종목수={}, cbState={}]", stockCodes.size(), cbState);

            List<CompletableFuture<Void>> futures = stockCodes.stream()
                    .map(code -> CompletableFuture.runAsync(() -> collectSingle(code, traceId),
                            kisExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("장중 시세 수집 완료");
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
            kisRetryTemplate.execute(ctx -> {
                if (ctx.getRetryCount() > 0) {
                    log.warn("KIS 시세 재시도 [stockCode={}, attempt={}]", stockCode,
                            ctx.getRetryCount() + 1);
                }
                kisRateLimiter.acquirePermission();
                KisMarketResponse response = marketClient.fetchCurrentPrice(
                        "FHKST01010100", "J", stockCode);

                if (!"0".equals(response.returnCode())) {
                    log.warn("KIS 시세 조회 실패 [stockCode={}, msg={}]", stockCode, response.message());
                    return null;
                }

                KisMarketResponse.Output output = response.output();
                String stockName = collectorProperties.getStockName(stockCode);
                RawMarketEvent event = new RawMarketEvent(
                        stockCode,
                        stockName,
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
                return null;
            });
            kisCircuitBreaker.onSuccess(System.nanoTime() - startNano,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            kisCircuitBreaker.onError(System.nanoTime() - startNano,
                    java.util.concurrent.TimeUnit.NANOSECONDS, e);
            log.error("장중 시세 수집 실패 [stockCode={}]", stockCode, e);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
