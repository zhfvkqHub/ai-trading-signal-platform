package com.signal.signalservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "feedback")
public class FeedbackProperties {

    /** 신호 발생 후 수익률을 측정할 때까지 대기 시간(분) */
    private int checkWindowMinutes = 180;

    /** HIT 판정 기준 목표 수익률(%) */
    private double targetReturnPercent = 3.0;

    /** 결과 리스트 최대 보관 건수 (per 신호 타입 조합) */
    private int maxOutcomeHistory = 200;
}
