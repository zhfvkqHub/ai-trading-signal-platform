package com.signal.collectorservice.client.kis.afterhours;

import com.signal.collectorservice.config.feign.KisApiConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "kis-after-hours",
        url = "${kis.base-url}",
        configuration = KisApiConfig.class
)
public interface KisAfterHoursFeignClient {

    @GetMapping("/uapi/domestic-stock/v1/quotations/inquire-overtime-price")
    KisAfterHoursResponse fetchAfterHoursPrice(
            @RequestHeader("tr_id") String trId,
            @RequestParam("FID_COND_MRKT_DIV_CODE") String marketDivCode,
            @RequestParam("FID_INPUT_ISCD") String stockCode
    );

    /**
     * 시간외호가 잔량 조회 — 매수/매도 총 잔량을 반환한다.
     */
    @GetMapping("/uapi/domestic-stock/v1/quotations/inquire-overtime-asking-price-exp-ccn")
    KisAfterHoursOrderBookResponse fetchAfterHoursOrderBook(
            @RequestHeader("tr_id") String trId,
            @RequestParam("FID_COND_MRKT_DIV_CODE") String marketDivCode,
            @RequestParam("FID_INPUT_ISCD") String stockCode
    );
}
