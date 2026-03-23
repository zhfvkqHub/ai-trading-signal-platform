package com.signal.notificationservice.sender;

import com.signal.notificationservice.config.properties.TelegramProperties;
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
public class TelegramNotificationSender implements NotificationSender {

    @Qualifier("telegramWebClient")
    private final WebClient telegramWebClient;
    private final TelegramProperties telegramProperties;
    private final SignalMessageFormatter formatter;

    @Override
    public String getChannelName() {
        return "telegram";
    }

    @Override
    public boolean isEnabled() {
        return telegramProperties.isEnabled()
                && telegramProperties.getBotToken() != null
                && !telegramProperties.getBotToken()
                .isBlank()
                && telegramProperties.getChatId() != null
                && !telegramProperties.getChatId()
                .isBlank();
    }

    @Override
    public void send(SignalDetectedEvent event) {
        String message = formatter.formatForTelegram(event);
        String url = "/bot" + telegramProperties.getBotToken() + "/sendMessage";

        Map<String, String> body = Map.of(
                "chat_id", telegramProperties.getChatId(),
                "text", message,
                "parse_mode", "Markdown"
        );

        telegramWebClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .map(errorBody -> new RuntimeException(
                                        "Telegram API 오류: HTTP " + response.statusCode() + " - "
                                                + errorBody))
                )
                .bodyToMono(String.class)
                .block();

        log.info("[TELEGRAM] 알림 전송 완료 [stockCode={}, chatId={}]",
                event.stockCode(), telegramProperties.getChatId());
    }
}
