package com.signal.signalservice.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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
        /**
         * 거래량 급증 시 현재가가 시가 대비 상승 중인지 확인 여부
         */
        private boolean requirePriceUp = true;
        /**
         * requirePriceUp=true 일 때 허용할 최소 시가대비 상승률(%) — 0이면 보합도 허용
         */
        private double minPriceChangeRate = 0.0;
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
        /**
         * 신호 발생을 허용할 최소 매수 잔량. 소량 거래 노이즈 필터링
         */
        private long minBuyVolume = 100000;
    }

    @Getter
    @Setter
    public static class NewsSurge {
        private int thresholdCount = 3;
        private int windowMinutes = 30;
        /**
         * reportName에 포함 시 호재로 분류하는 키워드
         */
        private List<String> bullishKeywords = List.of(
                "실적", "흑자", "배당", "자사주", "수주", "계약", "특허", "합병", "인수",
                "성장", "증가", "호실적", "영업이익", "매출증가", "신제품", "상장"
        );
        /**
         * reportName에 포함 시 악재로 분류하여 신호를 제외하는 키워드
         */
        private List<String> bearishKeywords = List.of(
                "감사의견", "횡령", "불성실", "조사", "소송", "적자", "부도", "파산",
                "위반", "과징금", "제재", "거래정지", "상장폐지", "분식"
        );
    }
}
