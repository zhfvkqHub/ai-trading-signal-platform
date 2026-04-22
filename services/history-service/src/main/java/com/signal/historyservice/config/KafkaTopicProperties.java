package com.signal.historyservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicProperties {
    private String signalDetected;
    private String signalRejected;
    private String notificationDispatched;
}
