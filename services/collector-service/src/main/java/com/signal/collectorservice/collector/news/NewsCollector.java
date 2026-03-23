package com.signal.collectorservice.collector.news;

import com.signal.collectorservice.client.dart.DartDisclosureFeignClient;
import com.signal.collectorservice.client.dart.DartDisclosureResponse;
import com.signal.collectorservice.config.properties.CollectorProperties;
import com.signal.collectorservice.kafka.RawEventPublisher;
import com.signal.collectorservice.model.raw.RawNewsEvent;
import com.signal.collectorservice.schedule.TradingSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCollector {

    private final DartDisclosureFeignClient dartClient;
    private final TradingSessionManager sessionManager;
    private final CollectorProperties collectorProperties;
    private final RawEventPublisher eventPublisher;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private volatile String lastReceiptNo;

    /**
     * 매일 정해진 시간에 DART 공시보고서 조회 API를 호출하여 신규 공시보고서를 수집한다.
     - 수집 대상: 오늘 접수된 공시보고서 중 모니터링 대상 종목에 해당하는 보고서
     - 중복 방지: 마지막으로 수집한 접수번호 이후의 보고서만 처리
     - 발행: 수집된 보고서를 Kafka로 발행하여 후속 처리 시스템에서 소비하도록 함
     */
    @Scheduled(cron = "${collector.schedule.news.cron}")
    public void collect() {
        if (!sessionManager.isBusinessDay()) {
            return;
        }

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);

        try {
            String today = LocalDate.now(KST).format(DATE_FORMAT);
            log.info("공시 수집 시작 [date={}]", today);

            DartDisclosureResponse response = dartClient.fetchDisclosures(today, 1, 100);

            if (!"000".equals(response.status())) {
                log.warn("DART 공시 조회 실패 [status={}, msg={}]", response.status(), response.message());
                return;
            }

            List<DartDisclosureResponse.Item> items = response.list();
            if (items == null || items.isEmpty()) {
                log.debug("신규 공시 없음");
                return;
            }

            Set<String> monitoredCodes = Set.copyOf(collectorProperties.getStockCodes());
            int published = 0;

            for (DartDisclosureResponse.Item item : items) {
                // 중복 방지: 이전 수집 이후 접수번호만 처리
                if (lastReceiptNo != null && item.receiptNo().compareTo(lastReceiptNo) <= 0) {
                    continue;
                }

                // 모니터링 종목 필터링
                if (item.stockCode() == null || item.stockCode().isBlank()) {
                    continue;
                }
                if (!monitoredCodes.contains(item.stockCode())) {
                    continue;
                }

                RawNewsEvent event = new RawNewsEvent(
                        item.stockCode(),
                        item.corpName(),
                        item.reportName(),
                        item.receiptNo(),
                        item.receiptDate(),
                        item.filerName(),
                        item.corpClass(),
                        Instant.now(),
                        "DART",
                        traceId
                );

                eventPublisher.publishNewsEvent(item.receiptNo(), event);
                published++;
            }

            // 마지막 접수번호 갱신
            if (!items.isEmpty()) {
                lastReceiptNo = items.getFirst().receiptNo();
            }

            log.info("공시 수집 완료 [total={}, published={}]", items.size(), published);
        } catch (Exception e) {
            log.error("공시 수집 실패", e);
        } finally {
            MDC.remove("traceId");
        }
    }
}
