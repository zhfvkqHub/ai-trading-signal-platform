package com.signal.collectorservice.config;

import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.config.properties.DartProperties;
import com.signal.collectorservice.config.properties.KafkaTopicProperties;
import com.signal.collectorservice.config.properties.KisProperties;
import com.signal.collectorservice.config.properties.TradingSessionProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

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
}
