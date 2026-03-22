package com.signal.collectorservice.config.feign;

import com.signal.collectorservice.client.kis.auth.KisTokenManager;
import com.signal.collectorservice.config.KisProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * KIS API 공통 헤더 주입 인터셉터. 인증이 필요한 모든 KIS 클라이언트(시세, 시간외)에 적용된다. 토큰 발급용 KisAuthFeignClient 에는 적용하지
 * 않는다.
 */
@RequiredArgsConstructor
public class KisRequestInterceptor implements RequestInterceptor {

    private final KisTokenManager tokenManager;
    private final KisProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        template.header("authorization", "Bearer " + tokenManager.getAccessToken());
        template.header("appkey", properties.getAppKey());
        template.header("appsecret", properties.getAppSecret());
        template.header("custtype", "P");  // P: 개인
    }
}
