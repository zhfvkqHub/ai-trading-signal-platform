package com.signal.collectorservice.client.kis.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * KIS 토큰 발급 전용 클라이언트. 인증 전 요청이므로 KisApiConfig(인터셉터) 를 적용하지 않는다.
 */
@FeignClient(name = "kis-auth", url = "${kis.base-url}")
public interface KisAuthFeignClient {

    @PostMapping("/oauth2/tokenP")
    KisTokenResponse fetchToken(@RequestBody Map<String, String> body);
}
