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
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolumeSurgeScanner {

    private final StringRedisTemplate redisTemplate;
    private final ScannerProperties scannerProperties;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ScanResult scan(RawMarketEvent event) {
        ScannerProperties.VolumeSurge config = scannerProperties.getVolumeSurge();
        String stockCode = event.stockCode();
        BigDecimal currentVolume = event.volume();

        // 최소 거래량 필터
        if (currentVolume.longValue() < config.getMinVolume()) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        // EOD 거래량 조회 (전일)
        String yesterday = LocalDate.now(KST).minusDays(1).format(DATE_FORMAT);
        String eodKey = String.format(RedisKeyConstants.VOLUME_EOD, yesterday, stockCode);
        String eodVolumeStr = redisTemplate.opsForValue().get(eodKey);

        // 현재 누적 거래량을 last 키에 갱신 (last-write-wins)
        String lastKey = String.format(RedisKeyConstants.VOLUME_LAST, stockCode);
        redisTemplate.opsForValue().set(lastKey, currentVolume.toPlainString(),
                Duration.ofHours(config.getLastVolumeTtlHours()));

        if (eodVolumeStr == null) {
            // EOD 데이터 없으면 현재 데이터를 전일 EOD 기준으로 부트스트랩
            // → 이후 이벤트부터 이 기준 대비 거래량 비교 가능
            log.info("[VOLUME_SURGE] EOD 부트스트랩 [stockCode={}, baselineVolume={}]",
                    stockCode, currentVolume.toPlainString());
            redisTemplate.opsForValue().set(eodKey, currentVolume.toPlainString(),
                    Duration.ofHours(config.getEodTtlHours()));
            String todayEodKey = String.format(RedisKeyConstants.VOLUME_EOD,
                    LocalDate.now(KST).format(DATE_FORMAT), stockCode);
            redisTemplate.opsForValue().set(todayEodKey, currentVolume.toPlainString(),
                    Duration.ofHours(config.getEodTtlHours()));
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        BigDecimal eodVolume = new BigDecimal(eodVolumeStr);
        if (eodVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        double ratio = currentVolume.doubleValue() / eodVolume.doubleValue();

        // 오늘 EOD 스냅샷 갱신
        String todayEodKey = String.format(RedisKeyConstants.VOLUME_EOD,
                LocalDate.now(KST).format(DATE_FORMAT), stockCode);
        redisTemplate.opsForValue().set(todayEodKey, currentVolume.toPlainString(),
                Duration.ofHours(config.getEodTtlHours()));

        if (ratio >= config.getThresholdRatio()) {
            String reason = String.format("거래량 급증: 현재 %s / 전일 %s (%.1f배)",
                    currentVolume.toPlainString(), eodVolumeStr, ratio);
            log.info("[VOLUME_SURGE] {} - {}", stockCode, reason);
            return ScanResult.triggered(SignalType.VOLUME_SURGE, stockCode, reason, ratio);
        }

        return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
    }
}
