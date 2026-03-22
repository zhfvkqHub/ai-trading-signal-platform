package com.signal.collectorservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableRetry
@EnableFeignClients(basePackages = "com.signal.collectorservice.client")
@EnableConfigurationProperties(KisProperties.class)
public class AppConfig {
}
