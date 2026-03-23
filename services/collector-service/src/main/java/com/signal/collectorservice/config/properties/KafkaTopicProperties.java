package com.signal.collectorservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicProperties {

    private String rawMarket;
    private String rawAfterHours;
    private String rawNews;
}
