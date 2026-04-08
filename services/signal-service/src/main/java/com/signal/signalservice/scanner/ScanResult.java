package com.signal.signalservice.scanner;

import com.signal.signalservice.model.SignalType;

import java.util.Map;

public record ScanResult(
        SignalType signalType,
        String stockCode,
        boolean triggered,
        String reason,
        double rawScore,
        Map<String, String> metadata
) {

    public static ScanResult triggered(SignalType type, String stockCode, String reason, double rawScore) {
        return new ScanResult(type, stockCode, true, reason, rawScore, Map.of());
    }

    public static ScanResult triggered(SignalType type, String stockCode, String reason,
                                       double rawScore,
                                       Map<String, String> metadata) {
        return new ScanResult(type, stockCode, true, reason, rawScore, metadata);
    }

    public static ScanResult notTriggered(SignalType type, String stockCode) {
        return new ScanResult(type, stockCode, false, null, 0.0, Map.of());
    }

    public String getMetadata(String key) {
        return metadata.getOrDefault(key, "");
    }
}
