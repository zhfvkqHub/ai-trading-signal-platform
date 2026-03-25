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
    private int comboBonus2 = 10;
    private int comboBonus3 = 20;
    private int comboBonus4 = 30;
    private int minScore = 25;
    private Map<String, IntensityTier> intensity = Map.of();
    private int intensityTier2Bonus = 5;
    private int intensityTier3Bonus = 10;

    @Getter
    @Setter
    public static class IntensityTier {
        private double tier2Threshold;
        private double tier3Threshold;
    }
}
