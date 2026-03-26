package com.signal.collectorservice.config.feign;

import com.signal.collectorservice.client.kis.auth.KisTokenManager;
import com.signal.collectorservice.config.properties.KisProperties;
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
        // 수집 실패 시 다음 스케줄 주기(30초)에 자동 재시도됨
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Logger.Level kisFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
