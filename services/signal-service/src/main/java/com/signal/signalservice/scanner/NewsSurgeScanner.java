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
import java.util.Set;

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

        // [B3 수정] ZSET 슬라이딩 윈도우 — INCR+TTL 고정 버킷 방식 제거
        String zsetKey = String.format(RedisKeyConstants.NEWS_ZSET, stockCode);
        long nowEpoch = event.collectedAt()
                .getEpochSecond();
        long windowStart = nowEpoch - (long) config.getWindowMinutes() * 60;

        // receiptNo|reportName 형태로 멤버 구성 (감성 분석 재활용)
        String receiptNo = event.receiptNo() != null ? event.receiptNo() : String.valueOf(nowEpoch);
        // 파이프(|) 구분자 충돌 방지
        String member = receiptNo + "|" + reportName.replace("|", "/");

        // 현재 공시 추가 → 윈도우 밖 항목 제거 → 잔존 건수 조회
        redisTemplate.opsForZSet()
                .add(zsetKey, member, nowEpoch);
        redisTemplate.opsForZSet()
                .removeRangeByScore(zsetKey, 0, windowStart - 1);
        // 키 TTL: 윈도우 + 여유분 (만료 후 자동 정리)
        redisTemplate.expire(zsetKey, Duration.ofMinutes(config.getWindowMinutes() + 5));

        Set<String> windowMembers = redisTemplate.opsForZSet()
                .range(zsetKey, 0, -1);
        long count = windowMembers != null ? windowMembers.size() : 0;

        if (count >= config.getThresholdCount()) {
            // [B4 수정] 트리거 시점 단일 공시가 아닌 윈도우 전체 공시로 감성 판단
            boolean hasBullish = windowMembers.stream()
                    .map(m -> m.contains("|") ? m.substring(m.indexOf("|") + 1) : m)
                    .anyMatch(name -> isBullish(name, config) && !isBearish(name, config));

            String sentiment = hasBullish ? "BULLISH" : "NEUTRAL";
            String reason = String.format("공시 급증(%s): %d분 내 %d건 (최신: %s)",
                    sentiment, config.getWindowMinutes(), count, reportName);
            log.info("[NEWS_SURGE] {} - {}", stockCode, reason);
            return ScanResult.triggered(SignalType.NEWS_SURGE, stockCode, reason,
                    (double) count, Map.of("sentiment", sentiment));
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
