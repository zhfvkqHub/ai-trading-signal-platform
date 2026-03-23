package com.signal.notificationservice.config;

import com.signal.notificationservice.config.properties.TelegramProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("telegramWebClient")
    public WebClient telegramWebClient(TelegramProperties telegramProperties) {
        return WebClient.builder()
                .baseUrl(telegramProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    @Qualifier("slackWebClient")
    public WebClient slackWebClient() {
        // Slack은 Webhook URL 전체가 요청 URI이므로 baseUrl 없이 생성
        return WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
