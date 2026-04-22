package com.signal.notificationservice.model;

import java.time.Instant;
import java.util.List;

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
