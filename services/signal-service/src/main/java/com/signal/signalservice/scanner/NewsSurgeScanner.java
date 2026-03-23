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

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsSurgeScanner {

    private final StringRedisTemplate redisTemplate;
    private final ScannerProperties scannerProperties;

    public ScanResult scan(RawNewsEvent event) {
        ScannerProperties.NewsSurge config = scannerProperties.getNewsSurge();
        String stockCode = event.stockCode();

        // Redis INCR 카운터 + 윈도우 TTL
        String countKey = String.format(RedisKeyConstants.NEWS_COUNT, stockCode);
        Long count = redisTemplate.opsForValue().increment(countKey);

        if (count != null && count == 1) {
            // 첫 번째 카운트일 때 TTL 설정
            redisTemplate.expire(countKey, Duration.ofMinutes(config.getWindowMinutes()));
        }

        if (count != null && count >= config.getThresholdCount()) {
            String reason = String.format("뉴스 급증: %d분 내 %d건 (보고서: %s)",
                    config.getWindowMinutes(), count, event.reportName());
            log.info("[NEWS_SURGE] {} - {}", stockCode, reason);
            return ScanResult.triggered(SignalType.NEWS_SURGE, stockCode, reason, count.doubleValue());
        }

        return ScanResult.notTriggered(SignalType.NEWS_SURGE, stockCode);
    }
}
