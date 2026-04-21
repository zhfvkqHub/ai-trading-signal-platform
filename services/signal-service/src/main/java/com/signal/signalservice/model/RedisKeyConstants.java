package com.signal.signalservice.model;

public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    // Scanner keys
    public static final String VOLUME_EOD = "signal:volume:eod:%s:%s";          // {date}:{stockCode}
    public static final String VOLUME_LAST = "signal:volume:last:%s";            // {stockCode}
    public static final String AFTER_HOURS_PRICE = "signal:afterhours:price:%s"; // {stockCode}
    public static final String NEWS_ZSET = "signal:news:zset:%s";               // {stockCode} — ZSET 슬라이딩 윈도우

    // Breakout Scanner keys
    public static final String BREAKOUT_INTRADAY_HIGH = "signal:breakout:intraday:%s:%s"; // {yyyyMMdd}:{stockCode}
    public static final String BREAKOUT_HISTORY = "signal:breakout:history:%s";           // {stockCode} — ZSET

    // Validator keys
    public static final String DEDUP = "signal:dedup:%s:%s";       // {signalType}:{stockCode}
    public static final String COOLDOWN = "signal:cooldown:%s:%s";  // {stockCode}:{signalType}
    public static final String BURST = "signal:burst:%s";          // {stockCode}

    // Feedback keys
    public static final String FEEDBACK_PENDING = "signal:feedback:pending:%s";     // {stockCode}
    public static final String FEEDBACK_OUTCOMES = "signal:feedback:outcomes:%s";   // {signalTypesKey} — List
}
