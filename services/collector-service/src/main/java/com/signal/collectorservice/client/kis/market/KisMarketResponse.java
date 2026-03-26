package com.signal.collectorservice.client.kis.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisMarketResponse(
        @JsonProperty("rt_cd") String returnCode,      // "0" = 성공
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        @JsonProperty("output") Output output
) {
    public record Output(
            @JsonProperty("stck_prpr") String currentPrice,     // 현재가
            @JsonProperty("stck_oprc") String openPrice,        // 시가
            @JsonProperty("stck_hgpr") String highPrice,        // 고가
            @JsonProperty("stck_lwpr") String lowPrice,         // 저가
            @JsonProperty("stck_sdpr") String prevClosePrice,   // 전일 종가 (기준가)
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("acml_tr_pbmn") String tradingValue,  // 누적 거래대금
            @JsonProperty("bstp_kor_isnm") String stockName      // 종목명
    ) {
    }
}
