package com.signal.signalservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "scorer")
public class ScorerProperties {

    private Map<String, Integer> weights = Map.of();
    private int comboBonus = 10;
    private int minScore = 25;
}
