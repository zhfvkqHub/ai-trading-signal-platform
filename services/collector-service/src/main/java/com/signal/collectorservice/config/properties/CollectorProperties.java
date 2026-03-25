package com.signal.collectorservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "collector")
public class CollectorProperties {

    private List<String> stockCodes = List.of();
    private Schedule schedule = new Schedule();

    @Getter
    @Setter
    public static class Schedule {
        private CronEntry preMarket = new CronEntry();
        private CronEntry market = new CronEntry();
        private CronEntry afterHours = new CronEntry();
        private CronEntry news = new CronEntry();
    }

    @Getter
    @Setter
    public static class CronEntry {
        private String cron;
    }
}
