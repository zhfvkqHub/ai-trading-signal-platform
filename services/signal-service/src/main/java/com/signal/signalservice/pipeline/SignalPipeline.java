package com.signal.signalservice.pipeline;

import com.signal.signalservice.consumer.dto.RawAfterHoursEvent;
import com.signal.signalservice.consumer.dto.RawMarketEvent;
import com.signal.signalservice.consumer.dto.RawNewsEvent;
import com.signal.signalservice.feedback.SignalFeedbackTracker;
import com.signal.signalservice.model.SignalType;
import com.signal.signalservice.publisher.SignalDetectedEvent;
import com.signal.signalservice.publisher.SignalPublisher;
import com.signal.signalservice.publisher.SignalRejectedEvent;
import com.signal.signalservice.scanner.AfterHoursSurgeScanner;
import com.signal.signalservice.scanner.BreakoutScanner;
import com.signal.signalservice.scanner.GapUpScanner;
import com.signal.signalservice.scanner.NewsSurgeScanner;
import com.signal.signalservice.scanner.ScanResult;
import com.signal.signalservice.scanner.VolumeSurgeScanner;
import com.signal.signalservice.scorer.SignalScorer;
import com.signal.signalservice.validator.SignalValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scanner → Scorer → Validator → Publisher 오케스트레이션.
 *
 * <p>시장 이벤트 처리 시 피드백 루프(FeedbackTracker)가 함께 실행된다:
 * <ul>
 *   <li>이벤트 수신 시: 이전 신호의 수익률 결과 체크</li>
 *   <li>신호 발행 성공 시: 현재 가격을 기록해 추후 수익률 측정 준비</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalPipeline {

    private final VolumeSurgeScanner volumeSurgeScanner;
    private final GapUpScanner gapUpScanner;
    private final BreakoutScanner breakoutScanner;
    private final AfterHoursSurgeScanner afterHoursSurgeScanner;
    private final NewsSurgeScanner newsSurgeScanner;
    private final SignalScorer scorer;
    private final SignalValidator validator;
    private final SignalPublisher publisher;
    private final SignalFeedbackTracker feedbackTracker;

    /**
     * 시장 데이터 이벤트 처리: VolumeSurge + GapUp + Breakout 스캐너
     */
    public void processMarketEvent(RawMarketEvent event) {
        // 피드백 루프: 이전 신호 수익률 체크 (스캔보다 먼저 실행)
        feedbackTracker.checkOutcome(event);

        List<ScanResult> triggered = new ArrayList<>();

        ScanResult volumeResult = volumeSurgeScanner.scan(event);
        if (volumeResult.triggered()) triggered.add(volumeResult);

        ScanResult gapResult = gapUpScanner.scan(event);
        if (gapResult.triggered()) triggered.add(gapResult);

        ScanResult breakoutResult = breakoutScanner.scan(event);
        if (breakoutResult.triggered()) triggered.add(breakoutResult);

        if (triggered.isEmpty()) {
            log.debug("시장 데이터 분석 완료: 신호 미감지 [stockCode={}, volume={}]",
                    event.stockCode(), event.volume());
            return;
        }

        evaluateAndPublish(event.stockCode(), event.stockName(), triggered, event.price());
    }

    /**
     * 시간외 데이터 이벤트 처리: AfterHoursSurge 스캐너
     */
    public void processAfterHoursEvent(RawAfterHoursEvent event) {
        ScanResult result = afterHoursSurgeScanner.scan(event);
        if (!result.triggered()) {
            return;
        }

        String stockName = event.stockName() != null ? event.stockName() : event.stockCode();
        evaluateAndPublish(event.stockCode(), stockName, List.of(result), null);
    }

    /**
     * 뉴스 이벤트 처리: NewsSurge 스캐너
     */
    public void processNewsEvent(RawNewsEvent event) {
        ScanResult result = newsSurgeScanner.scan(event);
        if (!result.triggered()) {
            return;
        }

        String corpName = event.corpName() != null ? event.corpName() : event.stockCode();
        evaluateAndPublish(event.stockCode(), corpName, List.of(result), null);
    }

    /**
     * @param detectedPrice 시장 이벤트 기반 신호일 때의 감지 가격. 뉴스/시간외 이벤트는 null.
     */
    private void evaluateAndPublish(String stockCode, String stockName,
                                    List<ScanResult> triggered, BigDecimal detectedPrice) {
        String traceId = MDC.get("traceId");
        List<SignalType> signalTypes = triggered.stream()
                .map(ScanResult::signalType)
                .toList();
        List<String> reasons = triggered.stream()
                .map(ScanResult::reason)
                .toList();

        // Scorer
        int score = scorer.score(triggered);

        if (!scorer.meetsMinimum(score)) {
            log.debug("최소 점수 미달 [stockCode={}, score={}]", stockCode, score);
            publisher.publishRejected(new SignalRejectedEvent(
                    stockCode, stockName, signalTypes, score, "최소 점수 미달: " + score,
                    Instant.now(), traceId));
            return;
        }

        // Validator
        String rejectionReason = validator.validate(stockCode, signalTypes);
        if (rejectionReason != null) {
            publisher.publishRejected(new SignalRejectedEvent(
                    stockCode, stockName, signalTypes, score,
                    rejectionReason, Instant.now(), traceId));
            return;
        }

        // 유효 신호 발행 (recordSignalEmission이 false이면 쿨다운 레이스 — 발행 중단)
        boolean recorded = validator.recordSignalEmission(stockCode, signalTypes);
        if (!recorded) {
            publisher.publishRejected(new SignalRejectedEvent(
                    stockCode, stockName, signalTypes, score,
                    "쿨다운 레이스: 동시 신호 차단", Instant.now(), traceId));
            return;
        }

        publisher.publishDetected(new SignalDetectedEvent(
                stockCode, stockName, signalTypes, score,
                reasons, Instant.now(), traceId));

        // 피드백 루프: 신호 발행 성공 후 감지 가격 기록
        feedbackTracker.recordSignal(stockCode, detectedPrice, signalTypes, score, Instant.now());
    }
}
