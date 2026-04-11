package com.signal.signalservice.scanner;

import com.signal.signalservice.config.properties.ScannerProperties;
import com.signal.signalservice.consumer.dto.RawAfterHoursEvent;
import com.signal.signalservice.model.RedisKeyConstants;
import com.signal.signalservice.model.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AfterHoursSurgeScanner {

    private final StringRedisTemplate redisTemplate;
    private final ScannerProperties scannerProperties;

    public ScanResult scan(RawAfterHoursEvent event) {
        ScannerProperties.AfterHoursSurge config = scannerProperties.getAfterHoursSurge();
        String stockCode = event.stockCode();

        // 시간외 가격을 Redis에 캐싱 (GapUpScanner 교차 참조용)
        if (event.afterHoursPrice() != null && event.afterHoursPrice().compareTo(BigDecimal.ZERO) > 0) {
            String priceKey = String.format(RedisKeyConstants.AFTER_HOURS_PRICE, stockCode);
            redisTemplate.opsForValue().set(priceKey, event.afterHoursPrice().toPlainString(),
                    Duration.ofHours(config.getPriceTtlHours()));
        }

        BigDecimal buyVolume = event.buyOrderVolume();
        BigDecimal sellVolume = event.sellOrderVolume();

        if (buyVolume == null || sellVolume == null) {
            return ScanResult.notTriggered(SignalType.AFTER_HOURS_SURGE, stockCode);
        }

        // [B5 수정] 절대 매수 잔량 필터 — 소량 거래 노이즈 제거
        if (buyVolume.longValue() < config.getMinBuyVolume()) {
            log.debug("[AFTER_HOURS_SURGE] 최소 매수 잔량 미달 - 제외 [stockCode={}, buyVolume={}]",
                    stockCode, buyVolume.toPlainString());
            return ScanResult.notTriggered(SignalType.AFTER_HOURS_SURGE, stockCode);
        }

        // [B5 수정] 순매수(매도 0) 처리 — 기존엔 notTriggered 반환, 실제론 가장 강한 신호
        if (sellVolume.compareTo(BigDecimal.ZERO) == 0) {
            String reason = String.format("시간외 순매수 집중: 매수 %s / 매도 0 (순매수)",
                    buyVolume.toPlainString());
            log.info("[AFTER_HOURS_SURGE] {} - {}", stockCode, reason);
            // rawScore = 10.0 (tier3 임계값 4.0 초과 → 최대 강도 보너스)
            return ScanResult.triggered(SignalType.AFTER_HOURS_SURGE, stockCode, reason, 10.0);
        }

        double ratio = buyVolume.divide(sellVolume, 4, RoundingMode.HALF_UP).doubleValue();

        if (ratio >= config.getBuySellRatioThreshold()) {
            String reason = String.format("시간외 매수 급증: 매수 %s / 매도 %s (비율 %.2f)",
                    buyVolume.toPlainString(), sellVolume.toPlainString(), ratio);
            log.info("[AFTER_HOURS_SURGE] {} - {}", stockCode, reason);
            return ScanResult.triggered(SignalType.AFTER_HOURS_SURGE, stockCode, reason, ratio);
        }

        return ScanResult.notTriggered(SignalType.AFTER_HOURS_SURGE, stockCode);
    }
}
