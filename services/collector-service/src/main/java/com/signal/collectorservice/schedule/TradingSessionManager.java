package com.signal.collectorservice.schedule;

import com.signal.collectorservice.config.TradingSessionProperties;
import com.signal.collectorservice.model.TradingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TradingSessionManager {

    private final TradingSessionProperties sessionProperties;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 한국 고정 공휴일 (음력 공휴일 제외) */
    private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),   // 신정
            MonthDay.of(3, 1),   // 삼일절
            MonthDay.of(5, 5),   // 어린이날
            MonthDay.of(6, 6),   // 현충일
            MonthDay.of(8, 15),  // 광복절
            MonthDay.of(10, 3),  // 개천절
            MonthDay.of(10, 9),  // 한글날
            MonthDay.of(12, 25)  // 성탄절
    );

    public TradingSession getCurrentSession() {
        LocalTime now = LocalTime.now(KST);
        TradingSessionProperties.TimeRange preMarket = sessionProperties.getPreMarket();
        TradingSessionProperties.TimeRange regular = sessionProperties.getRegular();
        TradingSessionProperties.TimeRange afterHours = sessionProperties.getAfterHours();

        if (isWithin(now, preMarket)) {
            return TradingSession.PRE_MARKET;
        }
        if (isWithin(now, regular)) {
            return TradingSession.REGULAR;
        }
        if (isWithin(now, afterHours)) {
            return TradingSession.AFTER_HOURS;
        }
        return TradingSession.CLOSED;
    }

    public boolean isBusinessDay() {
        LocalDate today = LocalDate.now(KST);
        DayOfWeek dow = today.getDayOfWeek();

        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        if (FIXED_HOLIDAYS.contains(MonthDay.from(today))) {
            return false;
        }
        // TODO: 음력 공휴일(설날, 추석) — 추후 KRX 휴장일 API 또는 연별 리스트로 보완
        return true;
    }

    public boolean isSessionActive(TradingSession session) {
        return getCurrentSession() == session;
    }

    private boolean isWithin(LocalTime now, TradingSessionProperties.TimeRange range) {
        return !now.isBefore(range.getStart()) && now.isBefore(range.getEnd());
    }
}
