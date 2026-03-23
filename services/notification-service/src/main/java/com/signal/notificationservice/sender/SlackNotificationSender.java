package com.signal.notificationservice.sender;

import com.signal.notificationservice.config.properties.SlackProperties;
import com.signal.notificationservice.formatter.SignalMessageFormatter;
import com.signal.notificationservice.model.SignalDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationSender implements NotificationSender {

    @Qualifier("slackWebClient")
    private final WebClient slackWebClient;
    private final SlackProperties slackProperties;
    private final SignalMessageFormatter formatter;

    @Override
    public String getChannelName() {
        return "slack";
    }

    @Override
    public boolean isEnabled() {
        return slackProperties.isEnabled()
                && slackProperties.getWebhookUrl() != null
                && !slackProperties.getWebhookUrl()
                .isBlank();
    }

    @Override
    public void send(SignalDetectedEvent event) {
        String message = formatter.formatForSlack(event);

        Map<String, String> body = Map.of(
                "text", message,
                "username", slackProperties.getUsername(),
                "icon_emoji", slackProperties.getIconEmoji()
        );

        slackWebClient.post()
                .uri(slackProperties.getWebhookUrl())
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .map(errorBody -> new RuntimeException(
                                        "Slack Webhook 오류: HTTP " + response.statusCode() + " - "
                                                + errorBody))
                )
                .bodyToMono(String.class)
                .block();

        log.info("[SLACK] 알림 전송 완료 [stockCode={}]", event.stockCode());
    }
}
