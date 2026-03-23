package com.signal.signalservice.publisher;

import com.signal.signalservice.model.SignalType;

import java.time.Instant;
import java.util.List;

public record SignalDetectedEvent(
        String stockCode,
        String stockName,
        List<SignalType> signalTypes,
        int score,
        List<String> reasons,
        Instant detectedAt,
        String traceId
) {
}
