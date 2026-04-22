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
import java.util.Map;

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

        if (!passesMinVolumeFilter(config, currentVolume)) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }
        if (!passesTradingValueFilter(config, stockCode, event.tradingValue())) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        BigDecimal baselineVolume = resolveBaselineVolume(config, stockCode, currentVolume);
        if (baselineVolume == null || baselineVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        updateLastVolume(config, stockCode, currentVolume);

        double ratio = currentVolume.doubleValue() / baselineVolume.doubleValue();
        if (ratio < config.getThresholdRatio()) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        return buildTriggeredResult(config, stockCode, currentVolume, baselineVolume, ratio, event);
    }

    private boolean passesMinVolumeFilter(ScannerProperties.VolumeSurge config, BigDecimal currentVolume) {
        return currentVolume.longValue() >= config.getMinVolume();
    }

    // tradingValue=0이면 데이터 미제공으로 간주하여 통과 (소형주 노이즈 차단 목적)
    private boolean passesTradingValueFilter(ScannerProperties.VolumeSurge config, String stockCode,
            BigDecimal tradingValue) {
        if (config.getMinTradingValue() <= 0 || tradingValue == null
                || tradingValue.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        if (tradingValue.longValue() < config.getMinTradingValue()) {
            log.debug("[VOLUME_SURGE] 거래대금 미달 - 제외 [stockCode={}, tradingValue={}]",
                    stockCode, tradingValue.toPlainString());
            return false;
        }
        return true;
    }

    /**
     * 전일 EOD 거래량을 기준값으로 반환한다. EOD 키가 없으면 이전 세션 데이터로 부트스트랩을 시도하고,
     * 데이터 자체가 없으면 현재값으로 초기화한 후 null을 반환하여 이번 틱은 건너뛴다.
     */
    private BigDecimal resolveBaselineVolume(ScannerProperties.VolumeSurge config, String stockCode,
            BigDecimal currentVolume) {
        String eodKey = buildEodKey(stockCode);
        String eodVolumeStr = redisTemplate.opsForValue().get(eodKey);

        if (eodVolumeStr != null) {
            return new BigDecimal(eodVolumeStr);
        }

        return bootstrapEodVolume(config, stockCode, currentVolume, eodKey);
    }

    private BigDecimal bootstrapEodVolume(ScannerProperties.VolumeSurge config, String stockCode,
            BigDecimal currentVolume, String eodKey) {
        String lastKey = String.format(RedisKeyConstants.VOLUME_LAST, stockCode);
        String prevVolumeStr = redisTemplate.opsForValue().get(lastKey);

        if (prevVolumeStr != null) {
            // 이전 세션 종료 시 누적량으로 부트스트랩 — SET NX로 중복 갱신 방지
            redisTemplate.opsForValue().setIfAbsent(eodKey, prevVolumeStr,
                    Duration.ofHours(config.getEodTtlHours()));
            log.info("[VOLUME_SURGE] EOD 부트스트랩 (이전 세션 기준) [stockCode={}, baselineVolume={}]",
                    stockCode, prevVolumeStr);
            return new BigDecimal(prevVolumeStr);
        }

        // 이전 세션 데이터도 없으면 현재값으로 초기화, 다음 틱부터 비교
        redisTemplate.opsForValue().setIfAbsent(eodKey, currentVolume.toPlainString(),
                Duration.ofHours(config.getEodTtlHours()));
        redisTemplate.opsForValue().set(lastKey, currentVolume.toPlainString(),
                Duration.ofHours(config.getLastVolumeTtlHours()));
        log.info("[VOLUME_SURGE] EOD 부트스트랩 (초기값) [stockCode={}, baselineVolume={}]",
                stockCode, currentVolume.toPlainString());
        return null;
    }

    private void updateLastVolume(ScannerProperties.VolumeSurge config, String stockCode,
            BigDecimal currentVolume) {
        String lastKey = String.format(RedisKeyConstants.VOLUME_LAST, stockCode);
        redisTemplate.opsForValue().set(lastKey, currentVolume.toPlainString(),
                Duration.ofHours(config.getLastVolumeTtlHours()));
    }

    private ScanResult buildTriggeredResult(ScannerProperties.VolumeSurge config, String stockCode,
            BigDecimal currentVolume, BigDecimal baselineVolume, double ratio, RawMarketEvent event) {
        if (!config.isRequirePriceUp()) {
            return triggeredWithVolumeOnly(stockCode, currentVolume, baselineVolume, ratio);
        }

        Double priceChangeRate = calculatePriceChangeRate(event);
        if (priceChangeRate == null) {
            log.debug("[VOLUME_SURGE] 가격 데이터 미제공으로 방향 필터 적용 불가 - 제외 [stockCode={}]", stockCode);
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }
        if (priceChangeRate < config.getMinPriceChangeRate()) {
            log.debug("[VOLUME_SURGE] 가격 하락 중 거래량 급증 - 제외 [stockCode={}, priceChange={}%]",
                    stockCode, String.format("%.2f", priceChangeRate));
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        return triggeredWithPriceConfirmed(stockCode, currentVolume, baselineVolume, ratio, priceChangeRate);
    }

    // [S3] priceChangeRate를 메타데이터로 전달 → SignalScorer에서 상승률 구간별 강도 보너스 적용
    private ScanResult triggeredWithPriceConfirmed(String stockCode, BigDecimal currentVolume,
            BigDecimal baselineVolume, double ratio, double priceChangeRate) {
        String reason = String.format(
                "거래량 급증 + 상승 확인: 현재 %s / 전일 %s (%.1f배), 시가대비 +%.2f%%",
                currentVolume.toPlainString(), baselineVolume.toPlainString(), ratio, priceChangeRate);
        log.info("[VOLUME_SURGE] {} - {}", stockCode, reason);
        return ScanResult.triggered(SignalType.VOLUME_SURGE, stockCode, reason, ratio,
                Map.of("priceChangeRate", String.format("%.4f", priceChangeRate)));
    }

    private ScanResult triggeredWithVolumeOnly(String stockCode, BigDecimal currentVolume,
            BigDecimal baselineVolume, double ratio) {
        String reason = String.format("거래량 급증: 현재 %s / 전일 %s (%.1f배)",
                currentVolume.toPlainString(), baselineVolume.toPlainString(), ratio);
        log.info("[VOLUME_SURGE] {} - {}", stockCode, reason);
        return ScanResult.triggered(SignalType.VOLUME_SURGE, stockCode, reason, ratio);
    }

    // [B7] 가격 데이터 없으면 null 반환하여 방향 필터 적용 불가로 처리
    private Double calculatePriceChangeRate(RawMarketEvent event) {
        if (event.openPrice() == null || event.openPrice().compareTo(BigDecimal.ZERO) <= 0
                || event.price() == null) {
            return null;
        }
        return event.price()
                .subtract(event.openPrice())
                .divide(event.openPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private String buildEodKey(String stockCode) {
        String yesterday = LocalDate.now(KST).minusDays(1).format(DATE_FORMAT);
        return String.format(RedisKeyConstants.VOLUME_EOD, yesterday, stockCode);
    }
}
