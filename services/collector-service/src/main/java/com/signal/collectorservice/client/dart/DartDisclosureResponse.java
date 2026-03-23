package com.signal.collectorservice.client.dart;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DartDisclosureResponse(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message,
        @JsonProperty("page_no") int pageNo,
        @JsonProperty("page_count") int pageCount,
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("total_page") int totalPage,
        @JsonProperty("list") List<Item> list
) {
    public record Item(
            @JsonProperty("corp_cls") String corpClass,      // 법인구분 (Y:유가, K:코스닥 등)
            @JsonProperty("corp_name") String corpName,      // 법인명
            @JsonProperty("corp_code") String corpCode,      // 고유번호
            @JsonProperty("stock_code") String stockCode,    // 종목코드
            @JsonProperty("report_nm") String reportName,    // 보고서명
            @JsonProperty("rcept_no") String receiptNo,      // 접수번호
            @JsonProperty("flr_nm") String filerName,        // 공시 제출인명
            @JsonProperty("rcept_dt") String receiptDate,    // 접수일자 (YYYYMMDD)
            @JsonProperty("rm") String rm                     // 비고
    ) {
    }
}
