package com.signal.signalservice.scanner;

import com.signal.signalservice.config.properties.ScannerProperties;
import com.signal.signalservice.consumer.dto.RawMarketEvent;
import com.signal.signalservice.model.RedisKeyConstants;
import com.signal.signalservice.model.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Set;

/**
 * N일 최고가 돌파 스캐너 (D1 Breakout)
 *
 * <p>동작 방식:
 * <ol>
 *   <li>매 시장 이벤트마다 당일 인트라데이 고가를 Redis에 갱신한다.</li>
 *   <li>전일 고가가 ZSET(이력)에 아직 없으면 인트라데이 키에서 읽어 저장한다.</li>
 *   <li>N일 이력 ZSET에서 최고가를 계산한다.</li>
 *   <li>현재가가 N일 최고가를 minBreakoutPercent 이상 상회하면 신호를 발생한다.</li>
 * </ol>
 *
 * <p>별도의 history-service 없이 시장 데이터 스트림만으로 N일 이력을 자체 누적한다.
 * minDataDays일이 쌓이기 전까지는 신호가 발생하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BreakoutScanner {

    private final StringRedisTemplate redisTemplate;
    private final ScannerProperties scannerProperties;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ScanResult scan(RawMarketEvent event) {
        ScannerProperties.Breakout config = scannerProperties.getBreakout();
        String stockCode = event.stockCode();

        if (event.highPrice() == null || event.price() == null) {
            return ScanResult.notTriggered(SignalType.BREAKOUT, stockCode);
        }

        LocalDate today = event.collectedAt().atZone(KST).toLocalDate();
        long todayEpochDay = today.toEpochDay();
        String todayStr = today.format(DATE_FMT);

        // 1. 오늘 인트라데이 고가 갱신 (running max)
        String intradayKey = String.format(RedisKeyConstants.BREAKOUT_INTRADAY_HIGH, todayStr, stockCode);
        updateIntradayHigh(intradayKey, event.highPrice());

        // 2. 전일 고가를 이력 ZSET에 이동 (미저장 시)
        String histKey = String.format(RedisKeyConstants.BREAKOUT_HISTORY, stockCode);
        storeYesterdayHighIfAbsent(histKey, today, todayEpochDay, stockCode);

        // 3. N일 범위 밖 데이터 제거 + TTL 갱신
        redisTemplate.opsForZSet().removeRangeByScore(histKey, 0, todayEpochDay - config.getPeriodDays() - 1);
        redisTemplate.expire(histKey, Duration.ofDays(config.getPeriodDays() + 3));

        // 4. 이력 데이터 조회
        Set<String> histEntries = redisTemplate.opsForZSet().range(histKey, 0, -1);
        int dataCount = histEntries == null ? 0 : histEntries.size();

        if (dataCount < config.getMinDataDays()) {
            log.debug("[BREAKOUT] 이력 데이터 부족 - 대기 [stockCode={}, days={}/{}]",
                    stockCode, dataCount, config.getMinDataDays());
            return ScanResult.notTriggered(SignalType.BREAKOUT, stockCode);
        }

        // 5. N일 최고가 계산
        BigDecimal nDayHigh = histEntries.stream()
                .map(m -> m.split(":", 2)[1])
                .map(BigDecimal::new)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        if (nDayHigh.compareTo(BigDecimal.ZERO) <= 0) {
            return ScanResult.notTriggered(SignalType.BREAKOUT, stockCode);
        }

        // 6. 돌파율 계산
        double breakoutPct = event.price()
                .subtract(nDayHigh)
                .divide(nDayHigh, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        if (breakoutPct < config.getMinBreakoutPercent()) {
            return ScanResult.notTriggered(SignalType.BREAKOUT, stockCode);
        }

        String reason = String.format("%d일 최고가 돌파: 현재 %s / %d일 고 %s (+%.2f%%)",
                config.getPeriodDays(), event.price().toPlainString(),
                config.getPeriodDays(), nDayHigh.toPlainString(), breakoutPct);
        log.info("[BREAKOUT] {} - {}", stockCode, reason);
        return ScanResult.triggered(SignalType.BREAKOUT, stockCode, reason, breakoutPct);
    }

    /**
     * 오늘 인트라데이 최고가를 갱신한다. 기존 값보다 낮으면 유지.
     */
    private void updateIntradayHigh(String intradayKey, BigDecimal currentHigh) {
        String existingStr = redisTemplate.opsForValue().get(intradayKey);
        BigDecimal toStore = currentHigh;
        if (existingStr != null) {
            BigDecimal existing = new BigDecimal(existingStr);
            if (existing.compareTo(currentHigh) > 0) {
                toStore = existing;
            }
        }
        // TTL 2일: 주말 및 다음날 부트스트랩 커버
        redisTemplate.opsForValue().set(intradayKey, toStore.toPlainString(), Duration.ofDays(2));
    }

    /**
     * 전일 인트라데이 고가를 이력 ZSET에 저장한다. 이미 존재하면 건너뜀.
     *
     * <p>score=epochDay, member="{epochDay}:{highPrice}" 형태로 저장해
     * 날짜 기반 범위 삭제와 고가 파싱을 모두 지원한다.
     */
    private void storeYesterdayHighIfAbsent(String histKey, LocalDate today,
                                             long todayEpochDay, String stockCode) {
        long prevEpochDay = todayEpochDay - 1;
        Set<String> existing = redisTemplate.opsForZSet().rangeByScore(histKey, prevEpochDay, prevEpochDay);
        if (existing != null && !existing.isEmpty()) {
            return; // 이미 저장됨
        }

        LocalDate yesterday = today.minusDays(1);
        String prevIntradayKey = String.format(RedisKeyConstants.BREAKOUT_INTRADAY_HIGH,
                yesterday.format(DATE_FMT), stockCode);
        String prevHighStr = redisTemplate.opsForValue().get(prevIntradayKey);
        if (prevHighStr == null) {
            return; // 전일 장 데이터 없음 (주말 등)
        }

        String member = prevEpochDay + ":" + prevHighStr;
        redisTemplate.opsForZSet().add(histKey, member, prevEpochDay);
        log.debug("[BREAKOUT] 전일 고가 이력 저장 [stockCode={}, date={}, high={}]",
                stockCode, yesterday, prevHighStr);
    }
}
