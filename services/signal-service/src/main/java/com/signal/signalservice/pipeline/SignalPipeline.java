package com.signal.signalservice.pipeline;

import com.signal.signalservice.consumer.dto.RawAfterHoursEvent;
import com.signal.signalservice.consumer.dto.RawMarketEvent;
import com.signal.signalservice.consumer.dto.RawNewsEvent;
import com.signal.signalservice.model.SignalType;
import com.signal.signalservice.publisher.SignalDetectedEvent;
import com.signal.signalservice.publisher.SignalPublisher;
import com.signal.signalservice.publisher.SignalRejectedEvent;
import com.signal.signalservice.scanner.*;
import com.signal.signalservice.scorer.SignalScorer;
import com.signal.signalservice.validator.SignalValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scanner → Scorer → Validator → Publisher 오케스트레이션
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalPipeline {

    private final VolumeSurgeScanner volumeSurgeScanner;
    private final GapUpScanner gapUpScanner;
    private final AfterHoursSurgeScanner afterHoursSurgeScanner;
    private final NewsSurgeScanner newsSurgeScanner;
    private final SignalScorer scorer;
    private final SignalValidator validator;
    private final SignalPublisher publisher;

    /**
     * 시장 데이터 이벤트 처리: VolumeSurge + GapUp 스캐너
     */
    public void processMarketEvent(RawMarketEvent event) {
        List<ScanResult> triggered = new ArrayList<>();

        ScanResult volumeResult = volumeSurgeScanner.scan(event);
        if (volumeResult.triggered()) {
            triggered.add(volumeResult);
        }

        ScanResult gapResult = gapUpScanner.scan(event);
        if (gapResult.triggered()) {
            triggered.add(gapResult);
        }

        if (triggered.isEmpty()) {
            log.info("시장 데이터 분석 완료: 신호 미감지 [stockCode={}, volume={}, gapResult={}, volumeResult={}]",
                    event.stockCode(), event.volume(),
                    gapResult.triggered(), volumeResult.triggered());
            return;
        }

        String stockName = event.stockName() != null ? event.stockName() : event.stockCode();
        evaluateAndPublish(event.stockCode(), stockName, triggered);
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
        evaluateAndPublish(event.stockCode(), stockName, List.of(result));
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
        evaluateAndPublish(event.stockCode(), corpName, List.of(result));
    }

    private void evaluateAndPublish(String stockCode, String stockName, List<ScanResult> triggered) {
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
                    stockCode, stockName, signalTypes, score,
                    "최소 점수 미달: " + score, Instant.now(), traceId));
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

        // 유효 신호 발행
        validator.recordSignalEmission(stockCode, signalTypes);
        publisher.publishDetected(new SignalDetectedEvent(
                stockCode, stockName, signalTypes, score,
                reasons, Instant.now(), traceId));
    }
}
