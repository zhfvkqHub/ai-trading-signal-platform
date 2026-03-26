package com.signal.collectorservice.config.feign;

import com.signal.collectorservice.client.kis.auth.KisTokenManager;
import com.signal.collectorservice.config.properties.KisProperties;
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
        template.header("authorization", "Bearer "
                + "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0b2tlbiIsImF1ZCI6IjMyOTFlYTkzLTE0ZTYtNDI1ZC1hNzQ3LWVhOGE0ZGY1MDk1NSIsInByZHRfY2QiOiIiLCJpc3MiOiJ1bm9ndyIsImV4cCI6MTc3NDYxNzQ4MCwiaWF0IjoxNzc0NTMxMDgwLCJqdGkiOiJQU0Z0Z1c0WmJ1WHU1YWZBWmh2Y2RRckpHZlhycHpjNnc5ekwifQ.h2SzjLjmGIdH0A-OEYwOUFA3F4045hvsLiKfrB4II2pb0O6tnh35HaKeA3L-KOJpVIlzw4-PIbPqU2xp-vXQLA");
        template.header("appkey", properties.getAppKey());
        template.header("appsecret", properties.getAppSecret());
        template.header("custtype", "P");  // P: 개인
    }
}
