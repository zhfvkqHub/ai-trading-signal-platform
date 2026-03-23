package com.signal.collectorservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dart")
public class DartProperties {

    private String baseUrl;
    private String apiKey;
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {
        private int requestsPerSecond = 1;
    }
}
