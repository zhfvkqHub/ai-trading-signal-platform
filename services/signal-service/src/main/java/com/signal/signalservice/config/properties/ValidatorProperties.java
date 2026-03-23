package com.signal.signalservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {

    private int dedupTtlMinutes = 60;
    private int cooldownMinutes = 30;
    private int burstLimit = 5;
    private int burstWindowMinutes = 60;
}
