package com.signal.notificationservice.model;

public final class NotificationRedisKeyConstants {

    private NotificationRedisKeyConstants() {
    }

    // 종목별 전체 채널 공통 쿨다운
    // Key: notif:cooldown:{stockCode}
    // TTL: notification.rate-limit.cooldown-minutes
    public static final String COOLDOWN = "notif:cooldown:%s";

    // 채널별 중복 방지 (동일 채널 + 종목 + 신호 유형 조합)
    // Key: notif:dedup:{channel}:{stockCode}:{signalFingerprint}
    // TTL: notification.dedup.ttl-minutes
    public static final String DEDUP = "notif:dedup:%s:%s:%s";

    // 채널별 롤링 윈도우 발송 카운터
    // Key: notif:rate:{channel}:{windowKey}
    // TTL: notification.rate-limit.window-minutes
    public static final String RATE_WINDOW = "notif:rate:%s:%s";
}
