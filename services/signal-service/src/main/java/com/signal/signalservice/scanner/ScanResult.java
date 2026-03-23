package com.signal.signalservice.scanner;

import com.signal.signalservice.model.SignalType;

public record ScanResult(
        SignalType signalType,
        String stockCode,
        boolean triggered,
        String reason,
        double rawScore
) {

    public static ScanResult triggered(SignalType type, String stockCode, String reason, double rawScore) {
        return new ScanResult(type, stockCode, true, reason, rawScore);
    }

    public static ScanResult notTriggered(SignalType type, String stockCode) {
        return new ScanResult(type, stockCode, false, null, 0.0);
    }
}
