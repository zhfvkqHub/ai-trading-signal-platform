package com.signal.notificationservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.telegram")
public class TelegramProperties {
    private boolean enabled = true;
    private String botToken;
    private String chatId;
    private String baseUrl = "https://api.telegram.org";
}
