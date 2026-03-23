package com.signal.collectorservice.config;

import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.config.properties.DartProperties;
import com.signal.collectorservice.config.properties.KafkaTopicProperties;
import com.signal.collectorservice.config.properties.KisProperties;
import com.signal.collectorservice.config.properties.TradingSessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
