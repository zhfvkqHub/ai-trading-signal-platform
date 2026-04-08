package com.signal.signalservice.scorer;

import com.signal.signalservice.config.properties.ScorerProperties;
import com.signal.signalservice.model.SignalType;
import com.signal.signalservice.scanner.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalScorer {

    private final ScorerProperties scorerProperties;

    /**
     * 스캔 결과 목록에 대해 가중 점수를 계산한다.
     * - 각 SignalType별 가중치 적용
     * - rawScore 기반 강도 보너스 (tier2/tier3)
     * - 조합 품질 기반 콤보 보너스 (단순 카운트 → 신호 조합 우선순위 반영)
     * - 호재 공시(BULLISH) 추가 보너스
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

        // 조합 품질 기반 콤보 보너스
        int comboBonus = calculateComboBonus(triggeredResults);
        if (comboBonus > 0) {
            totalScore += comboBonus;
            log.debug("콤보 보너스: +{}점", comboBonus);
        }

        // 호재 공시 추가 보너스
        boolean hasBullishNews = triggeredResults.stream()
                .anyMatch(r -> r.signalType() == SignalType.NEWS_SURGE
                        && "BULLISH".equals(r.getMetadata("sentiment")));
        if (hasBullishNews) {
            int bullishBonus = scorerProperties.getBullishNewsBonus();
            totalScore += bullishBonus;
            log.debug("호재 공시 보너스: +{}점", bullishBonus);
        }

        // 100점 cap
        int finalScore = Math.min(totalScore, 100);
        log.debug("최종 점수: {}점 (cap 전: {}점)", finalScore, totalScore);

        return finalScore;
    }

    public boolean meetsMinimum(int score) {
        return score >= scorerProperties.getMinScore();
    }

    /**
     * 조합 품질 기반 콤보 보너스.
     *
     * <p>우선순위:
     * <ol>
     *   <li>프리미엄: 시간외급증 + 갭상승 + 거래량급증 → 방향성 3중 확인, 가장 신뢰도 높음</li>
     *   <li>강함: 시간외급증 + 갭상승 → 전일~당일 연속 상승 신호</li>
     *   <li>표준: 카운트 기반 (4개/3개/2개)</li>
     * </ol>
     */
    private int calculateComboBonus(List<ScanResult> results) {
        Set<SignalType> types = results.stream()
                .map(ScanResult::signalType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SignalType.class)));

        // 프리미엄 조합: 시간외 + 갭상승 + 거래량 (방향성 3중 확인)
        if (types.containsAll(
                Set.of(SignalType.AFTER_HOURS_SURGE, SignalType.GAP_UP, SignalType.VOLUME_SURGE))) {
            log.debug("콤보 판정: 프리미엄 조합 (시간외+갭상승+거래량)");
            return scorerProperties.getComboBonusPremium();
        }

        // 강한 조합: 시간외 + 갭상승 (전일 매수 압력 → 당일 갭상승)
        if (types.containsAll(Set.of(SignalType.AFTER_HOURS_SURGE, SignalType.GAP_UP))) {
            log.debug("콤보 판정: 강한 조합 (시간외+갭상승)");
            return scorerProperties.getComboBonus3();
        }

        // 표준: 단순 카운트 기반
        int count = results.size();
        if (count >= 4) {
            return scorerProperties.getComboBonus4();
        }
        if (count == 3) {
            return scorerProperties.getComboBonus3();
        }
        if (count == 2) {
            return scorerProperties.getComboBonus2();
        }
        return 0;
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
