package com.signal.historyservice.model;

import java.time.Instant;
import java.util.List;

// notification-service의 NotificationDispatchedEvent와 필드 구조를 동일하게 유지해야 JSON 역직렬화가 정상 동작합니다.
// 마이크로서비스 간 컴파일 의존성을 피하기 위해 별도 패키지에 복사본으로 정의합니다.
public record NotificationDispatchedEvent(
        String stockCode,
        String stockName,
        List<SignalType> signalTypes,
        int score,
        String channel,
        String status,       // SENT | SUPPRESSED
        String suppressReason,
        Instant dispatchedAt,
        String traceId
) {
}
