package com.signal.signalservice.consumer.dto;

import java.time.Instant;

public record RawNewsEvent(
        String stockCode,
        String corpName,
        String reportName,
        String receiptNo,
        String receiptDate,
        String filerName,
        String corpClass,
        Instant collectedAt,
        String source,
        String traceId
) {
}
