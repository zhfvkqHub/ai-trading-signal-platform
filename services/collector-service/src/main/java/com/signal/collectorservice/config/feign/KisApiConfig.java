package com.signal.collectorservice.config.feign;

import com.signal.collectorservice.client.kis.auth.KisTokenManager;
import com.signal.collectorservice.config.KisProperties;
import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * 인증이 필요한 KIS API 클라이언트(시세, 시간외)에 적용되는 Feign 설정. KisRequestInterceptor 를 통해 공통 헤더를 주입한다.
 */
public class KisApiConfig {

    @Bean
    public KisRequestInterceptor kisRequestInterceptor(
            KisTokenManager tokenManager, KisProperties properties) {
        return new KisRequestInterceptor(tokenManager, properties);
    }

    @Bean
    public KisErrorDecoder kisErrorDecoder() {
        return new KisErrorDecoder();
    }

    @Bean
    public Retryer kisRetryer() {
        // 429/5xx 시 100ms 시작, 최대 1s, 3회 재시도
        return new Retryer.Default(100, 1000, 3);
    }

    @Bean
    public Logger.Level kisFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
