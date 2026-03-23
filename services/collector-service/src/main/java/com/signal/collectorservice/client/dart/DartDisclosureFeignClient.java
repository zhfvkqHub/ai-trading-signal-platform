package com.signal.collectorservice.client.dart;

import com.signal.collectorservice.config.feign.DartApiConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "dart-disclosure",
        url = "${dart.base-url}",
        configuration = DartApiConfig.class
)
public interface DartDisclosureFeignClient {

    @GetMapping("/api/list.json")
    DartDisclosureResponse fetchDisclosures(
            @RequestParam("bgn_de") String beginDate,
            @RequestParam("page_no") int pageNo,
            @RequestParam("page_count") int pageCount
    );
}
