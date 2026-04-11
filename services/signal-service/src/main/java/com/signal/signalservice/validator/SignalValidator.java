package com.signal.signalservice.validator;

import com.signal.signalservice.config.properties.ValidatorProperties;
import com.signal.signalservice.model.RedisKeyConstants;
import com.signal.signalservice.model.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalValidator {

    private final StringRedisTemplate redisTemplate;
    private final ValidatorProperties validatorProperties;

    /**
     * 3단계 검증: dedup → cooldown → burst
     *
     * @return 거부 사유 (null이면 통과)
     */
    public String validate(String stockCode, List<SignalType> signalTypes) {
        // 1. Dedup: 동일 신호 타입 중복 방지
        for (SignalType type : signalTypes) {
            String dedupKey = String.format(RedisKeyConstants.DEDUP, type.name(), stockCode);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
                String reason = String.format("중복 신호: %s:%s (%d분 내 재발생)",
                        type.name(), stockCode, validatorProperties.getDedupTtlMinutes(type));
                log.debug("[DEDUP] {}", reason);
                return reason;
            }
        }

        // 2. Cooldown: 종목별 쿨다운
        String cooldownKey = String.format(RedisKeyConstants.COOLDOWN, stockCode);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            String reason = String.format("쿨다운 중: %s (%d분 이내 발신 이력)",
                    stockCode, validatorProperties.getCooldownMinutes());
            log.debug("[COOLDOWN] {}", reason);
            return reason;
        }

        // 3. Burst: 윈도우 내 최대 신호 수 제한
        String burstKey = String.format(RedisKeyConstants.BURST, stockCode);
        String burstCountStr = redisTemplate.opsForValue().get(burstKey);
        if (burstCountStr != null) {
            int burstCount = Integer.parseInt(burstCountStr);
            if (burstCount >= validatorProperties.getBurstLimit()) {
                String reason = String.format("버스트 제한: %s (%d분 내 %d건 초과)",
                        stockCode, validatorProperties.getBurstWindowMinutes(), validatorProperties.getBurstLimit());
                log.debug("[BURST] {}", reason);
                return reason;
            }
        }

        return null;
    }

    /**
     * 신호 발신 성공 후 Redis에 기록.
     *
     * <p>[B8 수정] 쿨다운 키를 SET NX EX로 원자적으로 획득한다. 동시 처리로 인해
     * validate() 통과 후 다른 스레드/컨슈머가 먼저 기록한 경우 false를 반환하며,
     * 호출자는 신호 발행을 중단해야 한다.
     *
     * @return true: 기록 성공(신호 발행 가능) / false: 레이스로 인한 쿨다운 선점 실패
     */
    public boolean recordSignalEmission(String stockCode, List<SignalType> signalTypes) {
        // 쿨다운 키 원자적 획득 (SET NX EX) — validate() 이후 동시 진입 차단
        String cooldownKey = String.format(RedisKeyConstants.COOLDOWN, stockCode);
        Boolean cooldownAcquired = redisTemplate.opsForValue()
                .setIfAbsent(
                        cooldownKey, "1",
                        Duration.ofMinutes(validatorProperties.getCooldownMinutes()));
        if (!Boolean.TRUE.equals(cooldownAcquired)) {
            log.debug("[COOLDOWN] 쿨다운 레이스 감지 - 신호 발행 중단 [stockCode={}]", stockCode);
            return false;
        }

        // Dedup 키 등록 (신호 타입별 TTL)
        for (SignalType type : signalTypes) {
            String dedupKey = String.format(RedisKeyConstants.DEDUP, type.name(), stockCode);
            int ttl = validatorProperties.getDedupTtlMinutes(type);
            redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofMinutes(ttl));
        }

        // Burst 카운터 증가 (INCR은 원자적)
        String burstKey = String.format(RedisKeyConstants.BURST, stockCode);
        Long count = redisTemplate.opsForValue().increment(burstKey);
        if (count != null && count == 1) {
            redisTemplate.expire(burstKey, Duration.ofMinutes(validatorProperties.getBurstWindowMinutes()));
        }

        return true;
    }
}
