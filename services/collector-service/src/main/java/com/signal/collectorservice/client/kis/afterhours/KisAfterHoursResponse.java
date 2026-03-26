package com.signal.collectorservice.client.kis.afterhours;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisAfterHoursResponse(
        @JsonProperty("rt_cd") String returnCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        @JsonProperty("output") Output output
) {
    public record Output(
            @JsonProperty("ovtm_untp_prpr") String afterHoursPrice,     // 시간외 단일가 현재가
            @JsonProperty("ovtm_untp_vol") String afterHoursVolume,     // 시간외 단일가 거래량
            @JsonProperty("ovtm_untp_tr_pbmn") String afterHoursValue,  // 시간외 단일가 거래대금
            @JsonProperty("ovtm_untp_mxpr") String upperLimitPrice,     // 시간외 상한가
            @JsonProperty("ovtm_untp_llam") String lowerLimitPrice,     // 시간외 하한가
            @JsonProperty("ovtm_untp_sdpr") String prevClosePrice,        // 시간외 단일가 기준가
            @JsonProperty("ovtm_untp_antc_cnqn") String expectedVolume, // 시간외 예상 체결량
            @JsonProperty("bstp_kor_isnm") String stockName            // 업종 한글 종목명
    ) {
    }
}
