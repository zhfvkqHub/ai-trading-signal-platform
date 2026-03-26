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
     * @return 거부 사유 (null이면 통과)
     */
    public String validate(String stockCode, List<SignalType> signalTypes) {
        // 1. Dedup: 동일 신호 타입 중복 방지
        for (SignalType type : signalTypes) {
            String dedupKey = String.format(RedisKeyConstants.DEDUP, type.name(), stockCode);
            if (redisTemplate.hasKey(dedupKey)) {
                String reason = String.format("중복 신호: %s:%s (TTL %d분 내 재발생)",
                        type.name(), stockCode, validatorProperties.getDedupTtlMinutes(type));
                log.debug("[DEDUP] {}", reason);
                return reason;
            }
        }

        // 2. Cooldown: 종목별 쿨다운
        String cooldownKey = String.format(RedisKeyConstants.COOLDOWN, stockCode);
        if (redisTemplate.hasKey(cooldownKey)) {
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
     * 신호 발신 성공 후 Redis에 기록
     */
    public void recordSignalEmission(String stockCode, List<SignalType> signalTypes) {
        // Dedup 키 등록 (신호 타입별 TTL 적용)
        for (SignalType type : signalTypes) {
            String dedupKey = String.format(RedisKeyConstants.DEDUP, type.name(), stockCode);
            int ttl = validatorProperties.getDedupTtlMinutes(type);
            redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofMinutes(ttl));
        }

        // Cooldown 키 등록
        String cooldownKey = String.format(RedisKeyConstants.COOLDOWN, stockCode);
        redisTemplate.opsForValue().set(cooldownKey, "1",
                Duration.ofMinutes(validatorProperties.getCooldownMinutes()));

        // Burst 카운터 증가
        String burstKey = String.format(RedisKeyConstants.BURST, stockCode);
        Long count = redisTemplate.opsForValue().increment(burstKey);
        if (count != null && count == 1) {
            redisTemplate.expire(burstKey, Duration.ofMinutes(validatorProperties.getBurstWindowMinutes()));
        }
    }
}
