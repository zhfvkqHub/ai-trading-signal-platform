package com.signal.notificationservice.dedup;

import com.signal.notificationservice.config.properties.NotificationProperties;
import com.signal.notificationservice.model.NotificationRedisKeyConstants;
import com.signal.notificationservice.model.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDedupService {

    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties notificationProperties;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern(
            "yyyyMMddHHmm");

    /**
     * 알림 억제 여부를 확인합니다. 억제 대상이면 억제 사유 문자열을 반환하고, 발송 허용이면 null을 반환합니다.
     */
    public String checkSuppression(String channel, String stockCode, List<SignalType> signalTypes) {
        // 1. 종목 쿨다운 확인 (전체 채널 공통)
        String cooldownKey = String.format(NotificationRedisKeyConstants.COOLDOWN, stockCode);
        if (redisTemplate.hasKey(cooldownKey)) {
            return String.format("쿨다운 중 [stockCode=%s, cooldown=%d분]",
                    stockCode, notificationProperties.getRateLimit()
                            .getCooldownMinutes());
        }

        // 2. 채널별 중복 방지 확인
        String fingerprint = buildSignalFingerprint(signalTypes);
        String dedupKey = String.format(NotificationRedisKeyConstants.DEDUP, channel, stockCode,
                fingerprint);
        if (redisTemplate.hasKey(dedupKey)) {
            return String.format("중복 억제 [channel=%s, stockCode=%s, signals=%s, ttl=%d분]",
                    channel, stockCode, fingerprint, notificationProperties.getDedup()
                            .getTtlMinutes());
        }

        // 3. 채널별 속도 제한 확인
        String windowKey = buildWindowKey();
        String rateKey = String.format(NotificationRedisKeyConstants.RATE_WINDOW, channel,
                windowKey);
        String countStr = redisTemplate.opsForValue()
                .get(rateKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        if (count >= notificationProperties.getRateLimit()
                .getMaxPerWindow()) {
            return String.format("속도 제한 초과 [channel=%s, window=%d분, limit=%d건]",
                    channel, notificationProperties.getRateLimit()
                            .getWindowMinutes(),
                    notificationProperties.getRateLimit()
                            .getMaxPerWindow());
        }

        return null;
    }

    /**
     * 발송 성공 후 Redis에 쿨다운 / dedup / rate window 기록합니다. 반드시 send() 성공 후에 호출해야 합니다.
     */
    public void recordDispatch(String channel, String stockCode, List<SignalType> signalTypes) {
        var rateLimit = notificationProperties.getRateLimit();
        var dedup = notificationProperties.getDedup();

        // 종목 쿨다운 등록
        String cooldownKey = String.format(NotificationRedisKeyConstants.COOLDOWN, stockCode);
        redisTemplate.opsForValue()
                .set(cooldownKey, "1",
                        Duration.ofMinutes(rateLimit.getCooldownMinutes()));

        // 채널별 dedup 등록
        String fingerprint = buildSignalFingerprint(signalTypes);
        String dedupKey = String.format(NotificationRedisKeyConstants.DEDUP, channel, stockCode,
                fingerprint);
        redisTemplate.opsForValue()
                .set(dedupKey, "1",
                        Duration.ofMinutes(dedup.getTtlMinutes()));

        // 채널별 롤링 윈도우 카운터 증가
        String windowKey = buildWindowKey();
        String rateKey = String.format(NotificationRedisKeyConstants.RATE_WINDOW, channel,
                windowKey);
        Long newCount = redisTemplate.opsForValue()
                .increment(rateKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(rateKey, Duration.ofMinutes(rateLimit.getWindowMinutes()));
        }
    }

    /**
     * 신호 유형 목록을 알파벳 순 정렬 후 '_' 로 연결한 지문 문자열을 생성합니다. 순서가 달라도 동일한 조합이면 같은 지문이 만들어집니다.
     */
    private String buildSignalFingerprint(List<SignalType> signalTypes) {
        return signalTypes.stream()
                .map(SignalType::name)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("_"));
    }

    /**
     * 현재 시각을 롤링 윈도우 크기로 절삭한 윈도우 키를 생성합니다. windowMinutes=60 이면 60분 단위로 키가 바뀝니다.
     */
    private String buildWindowKey() {
        int windowMinutes = notificationProperties.getRateLimit()
                .getWindowMinutes();
        LocalDateTime now = LocalDateTime.now(KST);
        int truncatedMinute = (now.getMinute() / windowMinutes) * windowMinutes;
        LocalDateTime windowStart = now.withMinute(truncatedMinute)
                .withSecond(0)
                .withNano(0);
        return windowStart.format(WINDOW_FORMAT);
    }
}
