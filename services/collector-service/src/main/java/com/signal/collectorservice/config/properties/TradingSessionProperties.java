package com.signal.collectorservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

@Getter
@Setter
@ConfigurationProperties(prefix = "trading.session")
public class TradingSessionProperties {

    private TimeRange preMarket = new TimeRange();
    private TimeRange regular = new TimeRange();
    private TimeRange afterHours = new TimeRange();

    @Getter
    @Setter
    public static class TimeRange {
        private LocalTime start;
        private LocalTime end;
    }
}
