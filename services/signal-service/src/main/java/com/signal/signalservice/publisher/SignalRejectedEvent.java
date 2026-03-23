package com.signal.signalservice.publisher;

import com.signal.signalservice.model.SignalType;

import java.time.Instant;
import java.util.List;

public record SignalRejectedEvent(
        String stockCode,
        String stockName,
        List<SignalType> signalTypes,
        int score,
        String rejectionReason,
        Instant rejectedAt,
        String traceId
) {
}
