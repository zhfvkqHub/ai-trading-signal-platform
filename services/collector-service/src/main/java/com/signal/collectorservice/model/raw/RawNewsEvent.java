package com.signal.collectorservice.model.raw;

import java.time.Instant;

public record RawNewsEvent(
        String stockCode,       // 종목코드
        String corpName,        // 법인명
        String reportName,      // 보고서명
        String receiptNo,       // 접수번호
        String receiptDate,     // 접수일자
        String filerName,       // 공시 제출인명
        String corpClass,       // 법인구분
        Instant collectedAt,    // 수집 시각
        String source,          // 데이터 소스
        String traceId
) {
}
