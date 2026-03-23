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

@Slf4j
@Component
@RequiredArgsConstructor
public class GapUpScanner {

    private final StringRedisTemplate redisTemplate;
    private final ScannerProperties scannerProperties;

    public ScanResult scan(RawMarketEvent event) {
        ScannerProperties.GapUp config = scannerProperties.getGapUp();
        String stockCode = event.stockCode();
        BigDecimal openPrice = event.openPrice();
        BigDecimal prevClosePrice = event.prevClosePrice();

        if (openPrice == null || prevClosePrice == null
                || prevClosePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return ScanResult.notTriggered(SignalType.GAP_UP, stockCode);
        }

        // 갭상승률 = (시가 - 전일종가) / 전일종가 * 100
        double gapPercent = openPrice.subtract(prevClosePrice)
                .divide(prevClosePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        if (gapPercent < config.getThresholdPercent()) {
            return ScanResult.notTriggered(SignalType.GAP_UP, stockCode);
        }

        // 시간외 데이터 교차 참조
        String afterHoursKey = String.format(RedisKeyConstants.AFTER_HOURS_PRICE, stockCode);
        String afterHoursPriceStr = redisTemplate.opsForValue().get(afterHoursKey);

        String reason;
        if (afterHoursPriceStr != null) {
            BigDecimal afterHoursPrice = new BigDecimal(afterHoursPriceStr);
            double afterHoursGap = afterHoursPrice.subtract(prevClosePrice)
                    .divide(prevClosePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            reason = String.format("갭상승: 시가 %s / 전일종가 %s (%.2f%%), 시간외가 %s (%.2f%%)",
                    openPrice, prevClosePrice, gapPercent, afterHoursPrice, afterHoursGap);
        } else {
            reason = String.format("갭상승: 시가 %s / 전일종가 %s (%.2f%%)",
                    openPrice, prevClosePrice, gapPercent);
        }

        log.info("[GAP_UP] {} - {}", stockCode, reason);
        return ScanResult.triggered(SignalType.GAP_UP, stockCode, reason, gapPercent);
    }
}
