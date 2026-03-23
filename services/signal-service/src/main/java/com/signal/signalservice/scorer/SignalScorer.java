package com.signal.signalservice.scorer;

import com.signal.signalservice.config.properties.ScorerProperties;
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
     * - 2개 이상 신호 동시 발생 시 콤보 보너스
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
        }

        // 콤보 보너스 (2개 이상)
        if (triggeredResults.size() >= 2) {
            totalScore += scorerProperties.getComboBonus();
            log.debug("콤보 보너스: +{}점 ({}개 신호)", scorerProperties.getComboBonus(), triggeredResults.size());
        }

        // 100점 cap
        int finalScore = Math.min(totalScore, 100);
        log.debug("최종 점수: {}점 (cap 전: {}점)", finalScore, totalScore);

        return finalScore;
    }

    public boolean meetsMinimum(int score) {
        return score >= scorerProperties.getMinScore();
    }
}
