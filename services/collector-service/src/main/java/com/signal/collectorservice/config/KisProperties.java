package com.signal.collectorservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private String baseUrl;
    private String appKey;
    private String appSecret;
    private String accountNo;
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {
        private int requestsPerSecond = 19;
    }
}
