package com.signal.notificationservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    // 알림 발송 최소 점수 (signal-service의 min-score보다 높게 설정 가능)
    private int minScore = 25;

    private RateLimit rateLimit = new RateLimit();
    private Dedup dedup = new Dedup();

    @Getter
    @Setter
    public static class RateLimit {
        // 채널별 롤링 윈도우 내 최대 발송 건수
        private int maxPerWindow = 10;
        // 롤링 윈도우 크기 (분)
        private int windowMinutes = 60;
        // 종목별 전체 채널 공통 쿨다운 (분)
        private int cooldownMinutes = 30;
    }

    @Getter
    @Setter
    public static class Dedup {
        // 동일 신호 (동일 종목 + 동일 유형 조합) 중복 방지 TTL (분)
        private int ttlMinutes = 60;
    }
}
