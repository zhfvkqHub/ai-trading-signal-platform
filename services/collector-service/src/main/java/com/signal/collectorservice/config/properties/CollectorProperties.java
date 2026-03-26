package com.signal.collectorservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "collector")
public class CollectorProperties {

    private List<String> stockCodes = List.of();
    private Map<String, String> stockNames = new HashMap<>();

    public String getStockName(String stockCode) {
        return stockNames.getOrDefault(stockCode, stockCode);
    }
    private Schedule schedule = new Schedule();

    @Getter
    @Setter
    public static class Schedule {
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
