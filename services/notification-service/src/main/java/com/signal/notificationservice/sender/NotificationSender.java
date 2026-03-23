package com.signal.notificationservice.sender;

import com.signal.notificationservice.model.SignalDetectedEvent;

public interface NotificationSender {

    /**
     * 채널 식별자 (Redis 키에 사용). 예: "telegram", "slack"
     */
    String getChannelName();

    /**
     * 채널이 활성 상태인지 반환합니다. enabled=false 이거나 필수 설정값(token/webhook-url)이 없으면 false를 반환합니다.
     */
    boolean isEnabled();

    /**
     * 이벤트를 포맷하여 채널로 발송합니다. 실패 시 RuntimeException을 던집니다.
     */
    void send(SignalDetectedEvent event);
}
