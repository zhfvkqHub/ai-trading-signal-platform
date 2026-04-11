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

        // 최소 거래량 필터
        if (currentVolume.longValue() < config.getMinVolume()) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        // [D5] 최소 거래대금 필터 — 소형주 노이즈 차단 (tradingValue=0이면 데이터 미제공으로 통과)
        if (config.getMinTradingValue() > 0 && event.tradingValue() != null
                && event.tradingValue()
                .compareTo(BigDecimal.ZERO) > 0
                && event.tradingValue()
                .longValue() < config.getMinTradingValue()) {
            log.debug("[VOLUME_SURGE] 거래대금 미달 - 제외 [stockCode={}, tradingValue={}]",
                    stockCode, event.tradingValue()
                            .toPlainString());
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        // 전일 EOD 거래량 조회 (read-only baseline — 매 틱 갱신하지 않음)
        String yesterday = LocalDate.now(KST).minusDays(1).format(DATE_FORMAT);
        String eodKey = String.format(RedisKeyConstants.VOLUME_EOD, yesterday, stockCode);
        String eodVolumeStr = redisTemplate.opsForValue().get(eodKey);

        String lastKey = String.format(RedisKeyConstants.VOLUME_LAST, stockCode);

        if (eodVolumeStr == null) {
            // 전일 EOD 없음 → lastKey(이전 세션 마지막 누적량)로 부트스트랩
            // SET NX로 다른 인스턴스/재처리가 덮어쓰는 것을 방지
            String prevVolumeStr = redisTemplate.opsForValue()
                    .get(lastKey);
            String baselineVolume =
                    (prevVolumeStr != null) ? prevVolumeStr : currentVolume.toPlainString();
            Boolean set = redisTemplate.opsForValue()
                    .setIfAbsent(eodKey, baselineVolume,
                    Duration.ofHours(config.getEodTtlHours()));
            if (Boolean.TRUE.equals(set)) {
                log.info("[VOLUME_SURGE] EOD 부트스트랩 [stockCode={}, baselineVolume={}]",
                        stockCode, baselineVolume);
            }
            // 현재 누적 거래량 갱신 (다음 세션 부트스트랩 기준값)
            redisTemplate.opsForValue()
                    .set(lastKey, currentVolume.toPlainString(),
                            Duration.ofHours(config.getLastVolumeTtlHours()));
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        // 현재 누적 거래량 갱신 (다음 세션 부트스트랩 기준값)
        redisTemplate.opsForValue()
                .set(lastKey, currentVolume.toPlainString(),
                        Duration.ofHours(config.getLastVolumeTtlHours()));

        BigDecimal eodVolume = new BigDecimal(eodVolumeStr);
        if (eodVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
        }

        double ratio = currentVolume.doubleValue() / eodVolume.doubleValue();

        if (ratio >= config.getThresholdRatio()) {
            // [B7 수정] requirePriceUp=true 일 때 가격 데이터 없으면 제외 (기존 코드는 역으로 통과시켰음)
            if (config.isRequirePriceUp()) {
                if (event.openPrice() == null || event.openPrice()
                        .compareTo(BigDecimal.ZERO) <= 0
                        || event.price() == null) {
                    log.debug("[VOLUME_SURGE] 가격 데이터 미제공으로 방향 필터 적용 불가 - 제외 [stockCode={}]",
                            stockCode);
                    return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
                }
                double priceChangeRate = event.price()
                        .subtract(event.openPrice())
                        .divide(event.openPrice(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                if (priceChangeRate < config.getMinPriceChangeRate()) {
                    log.debug("[VOLUME_SURGE] 가격 하락 중 거래량 급증 - 제외 [stockCode={}, priceChange={}%]",
                            stockCode, String.format("%.2f", priceChangeRate));
                    return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
                }
                String reason = String.format(
                        "거래량 급증 + 상승 확인: 현재 %s / 전일 %s (%.1f배), 시가대비 +%.2f%%",
                        currentVolume.toPlainString(), eodVolumeStr, ratio, priceChangeRate);
                log.info("[VOLUME_SURGE] {} - {}", stockCode, reason);
                // [S3] priceChangeRate를 메타데이터로 전달 → SignalScorer에서 상승률 구간별 강도 보너스 적용
                return ScanResult.triggered(SignalType.VOLUME_SURGE, stockCode, reason, ratio,
                        Map.of("priceChangeRate", String.format("%.4f", priceChangeRate)));
            }

            String reason = String.format("거래량 급증: 현재 %s / 전일 %s (%.1f배)",
                    currentVolume.toPlainString(), eodVolumeStr, ratio);
            log.info("[VOLUME_SURGE] {} - {}", stockCode, reason);
            return ScanResult.triggered(SignalType.VOLUME_SURGE, stockCode, reason, ratio);
        }

        return ScanResult.notTriggered(SignalType.VOLUME_SURGE, stockCode);
    }
}
