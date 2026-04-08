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
    /**
     * 프리미엄 조합(시간외급증+갭상승+거래량급증) 보너스 — 단순 카운트 기반 보너스를 대체
     */
    private int comboBonusPremium = 35;
    private int minScore = 25;
    private Map<String, IntensityTier> intensity = Map.of();
    private int intensityTier2Bonus = 5;
    private int intensityTier3Bonus = 10;
    /**
     * NEWS_SURGE가 호재(BULLISH)로 분류됐을 때 추가 보너스
     */
    private int bullishNewsBonus = 10;

    @Getter
    @Setter
    public static class IntensityTier {
        private double tier2Threshold;
        private double tier3Threshold;
    }
}
