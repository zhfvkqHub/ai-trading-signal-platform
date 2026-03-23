package com.signal.notificationservice.config;

import com.signal.notificationservice.config.properties.KafkaTopicProperties;
import com.signal.notificationservice.config.properties.NotificationProperties;
import com.signal.notificationservice.config.properties.SlackProperties;
import com.signal.notificationservice.config.properties.TelegramProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        KafkaTopicProperties.class,
        TelegramProperties.class,
        SlackProperties.class,
        NotificationProperties.class
})
public class AppConfig {
}
