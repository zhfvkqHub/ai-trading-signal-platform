package com.signal.notificationservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.slack")
public class SlackProperties {
    private boolean enabled = true;
    private String webhookUrl;
    private String username = "Trading Signal Bot";
    private String iconEmoji = ":chart_with_upwards_trend:";
}
