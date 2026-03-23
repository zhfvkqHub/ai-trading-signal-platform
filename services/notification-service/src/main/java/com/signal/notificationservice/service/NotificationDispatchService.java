package com.signal.notificationservice.service;

import com.signal.notificationservice.config.properties.NotificationProperties;
import com.signal.notificationservice.dedup.NotificationDedupService;
import com.signal.notificationservice.model.SignalDetectedEvent;
import com.signal.notificationservice.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    // Spring이 NotificationSender 구현체 전부를 리스트로 주입합니다.
    // 새 채널 추가 시 NotificationSender 구현 클래스만 추가하면 되고, 이 클래스는 변경 불필요.
    private final List<NotificationSender> senders;
    private final NotificationDedupService dedupService;
    private final NotificationProperties notificationProperties;

    public void dispatch(SignalDetectedEvent event) {
        // 점수 게이트
        if (event.score() < notificationProperties.getMinScore()) {
            log.debug("점수 미달, 알림 스킵 [stockCode={}, score={}, minScore={}]",
                    event.stockCode(), event.score(), notificationProperties.getMinScore());
            return;
        }

        boolean anyEnabled = senders.stream()
                .anyMatch(NotificationSender::isEnabled);
        if (!anyEnabled) {
            log.warn("활성화된 알림 채널이 없습니다. 알림을 발송하지 않습니다. [stockCode={}]", event.stockCode());
            return;
        }

        for (NotificationSender sender : senders) {
            String channel = sender.getChannelName();

            // 채널 활성화 확인
            if (!sender.isEnabled()) {
                log.debug("채널 비활성, 스킵 [channel={}]", channel);
                continue;
            }

            // 억제 정책 확인 (쿨다운 / dedup / rate limit)
            String suppressReason = dedupService.checkSuppression(channel, event.stockCode(),
                    event.signalTypes());
            if (suppressReason != null) {
                log.info("알림 억제 [channel={}, stockCode={}, reason={}]", channel, event.stockCode(),
                        suppressReason);
                continue;
            }

            // 발송
            try {
                sender.send(event);
                // 발송 성공 후에만 Redis 기록
                dedupService.recordDispatch(channel, event.stockCode(), event.signalTypes());
                log.info("알림 발송 완료 [channel={}, stockCode={}, score={}]",
                        channel, event.stockCode(), event.score());
            } catch (Exception e) {
                // 한 채널 실패가 다른 채널 발송을 막지 않도록 per-channel 예외 처리
                log.error("알림 발송 실패 [channel={}, stockCode={}]", channel, event.stockCode(), e);
            }
        }
    }
}
