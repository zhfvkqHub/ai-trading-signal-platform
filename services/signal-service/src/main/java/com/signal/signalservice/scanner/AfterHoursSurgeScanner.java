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

        // 시간외 데이터를 Redis에 캐싱 (GapUpScanner 교차 참조용)
        if (event.afterHoursPrice() != null && event.afterHoursPrice().compareTo(BigDecimal.ZERO) > 0) {
            String priceKey = String.format(RedisKeyConstants.AFTER_HOURS_PRICE, stockCode);
            redisTemplate.opsForValue().set(priceKey, event.afterHoursPrice().toPlainString(),
                    Duration.ofHours(config.getPriceTtlHours()));
        }

        BigDecimal buyVolume = event.buyOrderVolume();
        BigDecimal sellVolume = event.sellOrderVolume();

        if (buyVolume == null || sellVolume == null
                || sellVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return ScanResult.notTriggered(SignalType.AFTER_HOURS_SURGE, stockCode);
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
