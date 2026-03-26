package com.signal.notificationservice.formatter;

import com.signal.notificationservice.model.SignalDetectedEvent;
import com.signal.notificationservice.model.SignalType;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SignalMessageFormatter {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_FORMATTER = DateTimeFormatter.ofPattern(
            "MM/dd HH:mm");

    /**
     * Telegram용 메시지를 생성합니다. (Markdown 포맷)
     */
    public String formatForTelegram(SignalDetectedEvent event) {
        return buildMessage(event, "*", "`");
    }

    /**
     * Slack용 메시지를 생성합니다. (mrkdwn 포맷 — bold: *text*)
     */
    public String formatForSlack(SignalDetectedEvent event) {
        return buildMessage(event, "*", "`");
    }

    private String buildMessage(SignalDetectedEvent event, String bold, String code) {
        String time = ZonedDateTime.ofInstant(event.detectedAt(), KST)
                .format(KST_FORMATTER);
        String emoji = resolveEmoji(event.score());
        String signalLabels = buildSignalLabels(event);

        StringBuilder sb = new StringBuilder();
        sb.append(emoji)
                .append(" ")
                .append(bold)
                .append("매매신호 감지")
                .append(bold)
                .append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        String displayName = event.stockName() != null ? event.stockName() : event.stockCode();
        sb.append("종목: ")
                .append(bold)
                .append(displayName)
                .append(bold)
                .append(" (")
                .append(event.stockCode())
                .append(")\n");
        sb.append("신호: ")
                .append(signalLabels)
                .append("\n");
        sb.append("점수: ")
                .append(bold)
                .append(event.score())
                .append("점")
                .append(bold)
                .append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("근거:\n");
        for (String reason : event.reasons()) {
            sb.append("  • ")
                    .append(reason)
                    .append("\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("감지시각: ")
                .append(time)
                .append(" KST\n");
        sb.append("TraceID: ")
                .append(code)
                .append(event.traceId())
                .append(code);

        return sb.toString();
    }

    /**
     * 점수 구간별 이모지 반환. 80+ → 🔥 강력, 60+ → ⚡ 주목, 25+ → 📈 일반
     */
    private String resolveEmoji(int score) {
        if (score >= 80) {
            return "🔥";
        }
        if (score >= 60) {
            return "⚡";
        }
        return "📈";
    }

    /**
     * SignalType 목록을 한국어 레이블로 변환하여 ' + ' 로 연결합니다.
     */
    private String buildSignalLabels(SignalDetectedEvent event) {
        return event.signalTypes()
                .stream()
                .map(this::toKoreanLabel)
                .reduce((a, b) -> a + " + " + b)
                .orElse("알 수 없음");
    }

    private String toKoreanLabel(SignalType type) {
        return switch (type) {
            case VOLUME_SURGE -> "거래량급증";
            case NEWS_SURGE -> "뉴스급증";
            case AFTER_HOURS_SURGE -> "시간외매수급증";
            case GAP_UP -> "갭상승";
        };
    }
}
