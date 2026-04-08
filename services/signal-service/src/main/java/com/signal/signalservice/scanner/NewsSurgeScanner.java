package com.signal.signalservice.scanner;

import com.signal.signalservice.config.properties.ScannerProperties;
import com.signal.signalservice.consumer.dto.RawNewsEvent;
import com.signal.signalservice.model.RedisKeyConstants;
import com.signal.signalservice.model.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsSurgeScanner {

    private final StringRedisTemplate redisTemplate;
    private final ScannerProperties scannerProperties;

    public ScanResult scan(RawNewsEvent event) {
        ScannerProperties.NewsSurge config = scannerProperties.getNewsSurge();
        String stockCode = event.stockCode();
        String reportName = event.reportName() != null ? event.reportName() : "";

        // 악재 공시는 즉시 제외
        if (isBearish(reportName, config)) {
            log.debug("[NEWS_SURGE] 악재 공시 제외 [stockCode={}, report={}]", stockCode, reportName);
            return ScanResult.notTriggered(SignalType.NEWS_SURGE, stockCode);
        }

        // Redis INCR 카운터 + 윈도우 TTL
        String countKey = String.format(RedisKeyConstants.NEWS_COUNT, stockCode);
        Long count = redisTemplate.opsForValue().increment(countKey);

        if (count != null && count == 1) {
            // 첫 번째 카운트일 때 TTL 설정
            redisTemplate.expire(countKey, Duration.ofMinutes(config.getWindowMinutes()));
        }

        if (count != null && count >= config.getThresholdCount()) {
            boolean bullish = isBullish(reportName, config);
            String sentiment = bullish ? "BULLISH" : "NEUTRAL";
            String reason = String.format("공시 급증(%s): %d분 내 %d건 (보고서: %s)",
                    sentiment, config.getWindowMinutes(), count, reportName);
            log.info("[NEWS_SURGE] {} - {}", stockCode, reason);
            return ScanResult.triggered(SignalType.NEWS_SURGE, stockCode, reason,
                    count.doubleValue(),
                    Map.of("sentiment", sentiment));
        }

        return ScanResult.notTriggered(SignalType.NEWS_SURGE, stockCode);
    }

    private boolean isBullish(String reportName, ScannerProperties.NewsSurge config) {
        return config.getBullishKeywords()
                .stream()
                .anyMatch(reportName::contains);
    }

    private boolean isBearish(String reportName, ScannerProperties.NewsSurge config) {
        return config.getBearishKeywords()
                .stream()
                .anyMatch(reportName::contains);
    }
}
