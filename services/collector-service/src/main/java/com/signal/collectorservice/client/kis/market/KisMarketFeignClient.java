package com.signal.collectorservice.client.kis.market;

import com.signal.collectorservice.config.feign.KisApiConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "kis-market",
        url = "${kis.base-url}",
        configuration = KisApiConfig.class
)
public interface KisMarketFeignClient {

    @GetMapping("/uapi/domestic-stock/v1/quotations/inquire-price")
    KisMarketResponse fetchCurrentPrice(
            @RequestHeader("tr_id") String trId,
            @RequestParam("FID_COND_MRKT_DIV_CODE") String marketDivCode,
            @RequestParam("FID_INPUT_ISCD") String stockCode
    );
}
