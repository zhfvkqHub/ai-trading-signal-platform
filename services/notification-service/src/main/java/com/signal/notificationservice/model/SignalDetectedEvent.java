package com.signal.notificationservice.model;

import java.time.Instant;
import java.util.List;

// signal-service의 SignalDetectedEvent와 필드 구조를 동일하게 유지해야 JSON 역직렬화가 정상 동작합니다.
// 마이크로서비스 간 컴파일 의존성을 피하기 위해 별도 패키지에 복사본으로 정의합니다.
public record SignalDetectedEvent(
        String stockCode,
        String stockName,
        List<SignalType> signalTypes,
        int score,
        List<String> reasons,
        Instant detectedAt,
        String traceId
) {
}
