package com.signal.collectorservice.config;

import com.signal.collectorservice.config.feign.KisErrorDecoder;
import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.config.properties.DartProperties;
import com.signal.collectorservice.config.properties.KafkaTopicProperties;
import com.signal.collectorservice.config.properties.KisProperties;
import com.signal.collectorservice.config.properties.TradingSessionProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@EnableRetry
@EnableFeignClients(basePackages = "com.signal.collectorservice.client")
@EnableConfigurationProperties({
        KisProperties.class,
        TradingSessionProperties.class,
        DartProperties.class,
        CollectorProperties.class,
        KafkaTopicProperties.class
})
public class AppConfig {

    @Bean(name = "kisExecutor", destroyMethod = "shutdown")
    public ExecutorService kisExecutor() {
        return Executors.newFixedThreadPool(5);
    }

    @Bean
    public RetryTemplate kisRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(500)
                .retryOn(KisErrorDecoder.KisServerException.class)
                .build();
    }

    @Bean
    public RateLimiter kisRateLimiter(KisProperties kisProperties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(kisProperties.getRateLimit()
                        .getRequestsPerSecond())
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
        return RateLimiter.of("kis-api", config);
    }

    /**
     * KIS API 연속 타임아웃 시 Circuit Breaker로 요청을 차단하여 IP 차단 악순환을 방지한다. - 최근 10건 중 60% 이상 실패 → OPEN
     * (30초간 요청 차단) - HALF-OPEN에서 3건 시도 후 성공률 확인 → 성공 시 CLOSED 복귀
     */
    @Bean
    public CircuitBreaker kisCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(SocketTimeoutException.class,
                        java.net.ConnectException.class,
                        feign.RetryableException.class)
                .build();
        return CircuitBreaker.of("kis-market", config);
    }

    /**
     * Feign이 기본 Java URLConnection 대신 OkHttp를 사용하도록 명시적으로 빈을 등록한다. 커넥션 풀링으로 TCP 연결 재사용 → KIS 연결 거부
     * 빈도 감소.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();
    }
}
