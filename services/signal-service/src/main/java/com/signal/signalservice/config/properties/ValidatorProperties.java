package com.signal.signalservice.config.properties;

import com.signal.signalservice.model.SignalType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {

    private int dedupTtlMinutes = 60;
    /** 신호 타입별 dedup TTL 오버라이드 (분). 미설정 시 dedupTtlMinutes 사용 */
    private Map<String, Integer> dedupTtlOverrides = new HashMap<>();
    private int cooldownMinutes = 30;
    private int burstLimit = 5;
    private int burstWindowMinutes = 60;

    public int getDedupTtlMinutes(SignalType type) {
        return dedupTtlOverrides.getOrDefault(type.name(), dedupTtlMinutes);
    }
}
