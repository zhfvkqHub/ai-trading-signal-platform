package com.signal.collectorservice.model.raw;

import com.signal.collectorservice.model.DataSource;

import java.math.BigDecimal;
import java.time.Instant;

public record RawAfterHoursEvent(
        String stockCode,               // 종목코드
        String stockName,               // 종목명
        BigDecimal afterHoursPrice,     // 시간외 단일가
        BigDecimal afterHoursVolume,    // 시간외 거래량
        BigDecimal buyOrderVolume,      // 매수 대기 물량
        BigDecimal sellOrderVolume,     // 매도 대기 물량
        BigDecimal prevClosePrice,      // 당일 종가 (갭상승률 기준)
        Instant collectedAt,
        DataSource source,
        String traceId
) {
}
