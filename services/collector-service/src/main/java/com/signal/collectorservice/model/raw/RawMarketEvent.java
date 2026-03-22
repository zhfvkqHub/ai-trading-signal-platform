package com.signal.collectorservice.model.raw;

import com.signal.collectorservice.model.DataSource;

import java.math.BigDecimal;
import java.time.Instant;

public record RawMarketEvent(
        String stockCode,       // 종목코드
        String stockName,       // 종목명
        BigDecimal price,       // 현재가
        BigDecimal openPrice,   // 시가
        BigDecimal highPrice,   // 고가
        BigDecimal lowPrice,    // 저가
        BigDecimal prevClosePrice,  // 전일 종가
        BigDecimal volume,      // 누적 거래량
        BigDecimal tradingValue,    // 누적 거래대금
        Instant collectedAt,    // 수집 시각
        DataSource source,
        String traceId
) {
}
