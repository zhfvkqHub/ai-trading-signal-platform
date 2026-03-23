package com.signal.signalservice.config;

import com.signal.signalservice.config.properties.KafkaTopicProperties;
import com.signal.signalservice.config.properties.ScannerProperties;
import com.signal.signalservice.config.properties.ScorerProperties;
import com.signal.signalservice.config.properties.ValidatorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        KafkaTopicProperties.class,
        ScannerProperties.class,
        ScorerProperties.class,
        ValidatorProperties.class
})
public class AppConfig {
}
