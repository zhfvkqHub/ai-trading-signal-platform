package com.signal.signalservice.scorer;

import com.signal.signalservice.config.properties.ScorerProperties;
import com.signal.signalservice.model.SignalType;
import com.signal.signalservice.scanner.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalScorer {

    private final ScorerProperties scorerProperties;

    /**
     * 스캔 결과 목록에 대해 가중 점수를 계산한다.
     * - 각 SignalType별 가중치 적용
     * - rawScore 기반 강도 보너스 (tier2/tier3)
     * - 신호 개수별 단계적 콤보 보너스 (2개: +10, 3개: +20, 4개: +30)
     * - 최대 100점 cap
     */
    public int score(List<ScanResult> triggeredResults) {
        if (triggeredResults.isEmpty()) {
            return 0;
        }

        int totalScore = 0;

        for (ScanResult result : triggeredResults) {
            String typeName = result.signalType().name();
            int weight = scorerProperties.getWeights().getOrDefault(typeName, 0);
            totalScore += weight;
            log.debug("점수 계산: {} = {}점", typeName, weight);

            int intensityBonus = calculateIntensityBonus(result.signalType(), result.rawScore());
            if (intensityBonus > 0) {
                totalScore += intensityBonus;
                log.debug("강도 보너스: {} = +{}점 (rawScore={})", typeName, intensityBonus, result.rawScore());
            }
        }

        // 단계별 콤보 보너스
        int signalCount = triggeredResults.size();
        int comboBonus = 0;
        if (signalCount >= 4) {
            comboBonus = scorerProperties.getComboBonus4();
        } else if (signalCount == 3) {
            comboBonus = scorerProperties.getComboBonus3();
        } else if (signalCount == 2) {
            comboBonus = scorerProperties.getComboBonus2();
        }

        if (comboBonus > 0) {
            totalScore += comboBonus;
            log.debug("콤보 보너스: +{}점 ({}개 신호)", comboBonus, signalCount);
        }

        // 100점 cap
        int finalScore = Math.min(totalScore, 100);
        log.debug("최종 점수: {}점 (cap 전: {}점)", finalScore, totalScore);

        return finalScore;
    }

    public boolean meetsMinimum(int score) {
        return score >= scorerProperties.getMinScore();
    }

    private int calculateIntensityBonus(SignalType signalType, double rawScore) {
        ScorerProperties.IntensityTier tier = scorerProperties.getIntensity().get(signalType.name());
        if (tier == null) {
            return 0;
        }

        if (rawScore >= tier.getTier3Threshold()) {
            return scorerProperties.getIntensityTier3Bonus();
        } else if (rawScore >= tier.getTier2Threshold()) {
            return scorerProperties.getIntensityTier2Bonus();
        }

        return 0;
    }
}
