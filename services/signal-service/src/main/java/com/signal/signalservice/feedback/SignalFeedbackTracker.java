package com.signal.signalservice.feedback;

import com.signal.signalservice.config.properties.FeedbackProperties;
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
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 신호 발생 후 실제 수익률을 추적하는 피드백 루프.
 *
 * <p>동작 방식:
 * <ol>
 *   <li>{@link #recordSignal}: 신호 발생 시 감지 가격과 메타데이터를 Redis에 저장한다.</li>
 *   <li>{@link #checkOutcome}: 이후 시장 이벤트마다 checkWindowMinutes 경과 여부를 확인한다.
 *       경과 시 현재가와 비교해 수익률을 계산하고 결과를 기록한다.</li>
 * </ol>
 *
 * <p>결과는 신호 타입 조합별 Redis List에 최대 maxOutcomeHistory건 보관된다.
 * 운영자는 Redis CLI로 결과를 조회하거나 로그에서 [FEEDBACK] 태그로 필터링할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalFeedbackTracker {

    private final StringRedisTemplate redisTemplate;
    private final FeedbackProperties feedbackProperties;

    private static final String SEPARATOR = "|";
    private static final String OUTCOMES_KEY_SEPARATOR = "_";

    /**
     * 신호 발생 직후 감지 정보를 저장한다.
     *
     * <p>종목당 하나의 pending 항목만 유지한다 (setIfAbsent).
     * 이미 추적 중인 신호가 있으면 덮어쓰지 않는다.
     */
    public void recordSignal(String stockCode, BigDecimal detectedPrice,
                             List<SignalType> signalTypes, int score, Instant detectedAt) {
        if (detectedPrice == null || detectedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String pendingKey = String.format(RedisKeyConstants.FEEDBACK_PENDING, stockCode);
        String signalTypesCSV = signalTypes.stream()
                .map(SignalType::name)
                .collect(Collectors.joining(","));

        // format: "detectedPrice|signalTypesCSV|score|epochMillis"
        String value = detectedPrice.toPlainString() + SEPARATOR
                + signalTypesCSV + SEPARATOR
                + score + SEPARATOR
                + detectedAt.toEpochMilli();

        long ttlMinutes = feedbackProperties.getCheckWindowMinutes() + 120L;
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(pendingKey, value, Duration.ofMinutes(ttlMinutes));
        if (Boolean.TRUE.equals(stored)) {
            log.debug("[FEEDBACK] 신호 추적 시작 [stockCode={}, price={}, types={}, score={}]",
                    stockCode, detectedPrice.toPlainString(), signalTypesCSV, score);
        }
    }

    /**
     * 시장 이벤트마다 호출해 pending 신호의 수익률을 체크한다.
     *
     * <p>checkWindowMinutes가 경과한 경우에만 결과를 계산하고 Redis List에 저장한다.
     * 결과 저장 후 pending 키를 삭제한다.
     */
    public void checkOutcome(RawMarketEvent event) {
        String pendingKey = String.format(RedisKeyConstants.FEEDBACK_PENDING, event.stockCode());
        String pendingValue = redisTemplate.opsForValue().get(pendingKey);
        if (pendingValue == null) {
            return;
        }

        String[] parts = pendingValue.split("\\" + SEPARATOR, 4);
        if (parts.length != 4) {
            redisTemplate.delete(pendingKey);
            return;
        }

        BigDecimal detectedPrice = new BigDecimal(parts[0]);
        String signalTypesCSV = parts[1];
        int score = Integer.parseInt(parts[2]);
        Instant detectedAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));

        long elapsedMinutes = Duration.between(detectedAt, event.collectedAt()).toMinutes();
        if (elapsedMinutes < feedbackProperties.getCheckWindowMinutes()) {
            return; // 아직 체크 시간이 안 됨
        }

        if (event.price() == null) {
            redisTemplate.delete(pendingKey);
            return;
        }

        // 수익률 계산
        double returnPct = event.price()
                .subtract(detectedPrice)
                .divide(detectedPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        boolean hit = returnPct >= feedbackProperties.getTargetReturnPercent();

        // 결과 저장: "returnPct:HIT/MISS:score:detectedEpochSec"
        String outcomesKey = String.format(RedisKeyConstants.FEEDBACK_OUTCOMES,
                signalTypesCSV.replace(",", OUTCOMES_KEY_SEPARATOR));
        String outcomeEntry = String.format("%.2f:%s:%d:%d",
                returnPct, hit ? "HIT" : "MISS", score, detectedAt.getEpochSecond());

        redisTemplate.opsForList().leftPush(outcomesKey, outcomeEntry);
        redisTemplate.opsForList().trim(outcomesKey, 0, feedbackProperties.getMaxOutcomeHistory() - 1L);
        redisTemplate.expire(outcomesKey, Duration.ofDays(30));

        // 결과 로그 — [FEEDBACK] 태그로 필터링 가능
        log.info("[FEEDBACK] {} | types={} | score={} | {}분 후 | 수익률={:.2f}% | 목표={}% | {}",
                event.stockCode(), signalTypesCSV, score, elapsedMinutes,
                returnPct, feedbackProperties.getTargetReturnPercent(),
                hit ? "HIT ✓" : "MISS ✗");

        // 현재까지 누적된 해당 신호 타입의 적중률 요약 로그
        logHitRateSummary(outcomesKey, signalTypesCSV);

        redisTemplate.delete(pendingKey);
    }

    /**
     * Redis List의 최근 N건을 분석해 적중률과 평균 수익률을 로그로 출력한다.
     */
    private void logHitRateSummary(String outcomesKey, String signalTypesCSV) {
        List<String> recent = redisTemplate.opsForList().range(outcomesKey, 0, 49); // 최근 50건
        if (recent == null || recent.isEmpty()) return;

        long hits = recent.stream().filter(e -> e.contains(":HIT:")).count();
        double avgReturn = recent.stream()
                .mapToDouble(e -> Double.parseDouble(e.split(":")[0]))
                .average()
                .orElse(0.0);

        log.info("[FEEDBACK] 적중률 요약 | types={} | {}건 중 {}HIT ({:.1f}%) | 평균수익률={:.2f}%",
                signalTypesCSV, recent.size(), hits,
                (double) hits / recent.size() * 100, avgReturn);
    }
}
