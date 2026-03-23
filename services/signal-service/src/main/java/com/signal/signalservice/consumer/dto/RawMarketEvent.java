package com.signal.signalservice.consumer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RawMarketEvent(
        String stockCode,
        String stockName,
        BigDecimal price,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal prevClosePrice,
        BigDecimal volume,
        BigDecimal tradingValue,
        Instant collectedAt,
        String source,
        String traceId
) {
}
