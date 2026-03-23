package com.signal.collectorservice.config.feign;

import com.signal.collectorservice.config.DartProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * DART API 공통 쿼리 파라미터 주입 인터셉터.
 * KIS와 달리 인증 키를 쿼리 파라미터(crtfc_key)로 전달한다.
 */
@RequiredArgsConstructor
public class DartRequestInterceptor implements RequestInterceptor {

    private final DartProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        template.query("crtfc_key", properties.getApiKey());
    }
}
