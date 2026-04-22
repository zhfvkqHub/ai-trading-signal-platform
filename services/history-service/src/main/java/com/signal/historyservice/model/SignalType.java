package com.signal.historyservice.model;

public enum SignalType {
    VOLUME_SURGE,       // 거래량 급증
    NEWS_SURGE,         // 뉴스/공시 급증
    AFTER_HOURS_SURGE,  // 시간외 매수 급증
    GAP_UP,             // 갭상승
    BREAKOUT            // N일 최고가 돌파
}
