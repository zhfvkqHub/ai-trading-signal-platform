package com.signal.signalservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "scanner")
public class ScannerProperties {

    private VolumeSurge volumeSurge = new VolumeSurge();
    private GapUp gapUp = new GapUp();
    private AfterHoursSurge afterHoursSurge = new AfterHoursSurge();
    private NewsSurge newsSurge = new NewsSurge();

    @Getter
    @Setter
    public static class VolumeSurge {
        private double thresholdRatio = 2.0;
        private long minVolume = 10000;
        private int eodTtlHours = 48;
        private int lastVolumeTtlHours = 2;
    }

    @Getter
    @Setter
    public static class GapUp {
        private double thresholdPercent = 3.0;
        /** 장 시작(09:00) 후 갭상승 스캔 허용 시간(분). 이후에는 스캔하지 않음 */
        private int cutoffMinutes = 30;
    }

    @Getter
    @Setter
    public static class AfterHoursSurge {
        private double buySellRatioThreshold = 1.5;
        private int priceTtlHours = 18;
    }

    @Getter
    @Setter
    public static class NewsSurge {
        private int thresholdCount = 3;
        private int windowMinutes = 30;
    }
}
