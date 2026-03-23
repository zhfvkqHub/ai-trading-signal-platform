package com.signal.signalservice.consumer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RawAfterHoursEvent(
        String stockCode,
        String stockName,
        BigDecimal afterHoursPrice,
        BigDecimal afterHoursVolume,
        BigDecimal buyOrderVolume,
        BigDecimal sellOrderVolume,
        BigDecimal prevClosePrice,
        Instant collectedAt,
        String source,
        String traceId
) {
}
