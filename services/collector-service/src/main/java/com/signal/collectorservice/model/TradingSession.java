package com.signal.collectorservice.model;

public enum TradingSession {
    PRE_MARKET,   // 08:00 ~ 09:00
    REGULAR,      // 09:00 ~ 15:30
    AFTER_HOURS,  // 15:30 ~ 18:00
    CLOSED        // 그 외 (수집 안 함)
}
