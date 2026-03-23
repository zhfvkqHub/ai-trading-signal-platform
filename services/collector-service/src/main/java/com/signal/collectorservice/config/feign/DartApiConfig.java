package com.signal.collectorservice.config.feign;

import com.signal.collectorservice.config.DartProperties;
import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * DART API 클라이언트(공시)에 적용되는 Feign 설정.
 * DartRequestInterceptor 를 통해 crtfc_key 쿼리 파라미터를 주입한다.
 */
public class DartApiConfig {

    @Bean
    public DartRequestInterceptor dartRequestInterceptor(DartProperties properties) {
        return new DartRequestInterceptor(properties);
    }

    @Bean
    public DartErrorDecoder dartErrorDecoder() {
        return new DartErrorDecoder();
    }

    @Bean
    public Retryer dartRetryer() {
        // 429/5xx 시 200ms 시작, 최대 2s, 3회 재시도
        return new Retryer.Default(200, 2000, 3);
    }

    @Bean
    public Logger.Level dartFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
