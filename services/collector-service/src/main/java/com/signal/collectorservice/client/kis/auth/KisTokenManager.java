package com.signal.collectorservice.client.kis.auth;

import com.signal.collectorservice.config.properties.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisTokenManager {

    private final KisAuthFeignClient authFeignClient;
    private final KisProperties properties;

    private String cachedToken;
    private Instant expiresAt;
    private final ReentrantLock lock = new ReentrantLock();

    private static final long REFRESH_MARGIN_SECONDS = 600; // 만료 10분 전 갱신

    public String getAccessToken() {
        if (isValid()) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (isValid()) {
                return cachedToken;
            }
            refresh();
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    private boolean isValid() {
        return cachedToken != null
                && expiresAt != null
                && Instant.now()
                .isBefore(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS));
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void refresh() {
        log.info("KIS Access Token 갱신 중");
        KisTokenResponse response = authFeignClient.fetchToken(Map.of(
                "grant_type", "client_credentials",
                "appkey", properties.getAppKey(),
                "appsecret", properties.getAppSecret()
        ));
        this.cachedToken = response.accessToken();
        this.expiresAt = Instant.now()
                .plusSeconds(response.expiresIn());
        log.info("KIS Access Token 갱신 완료, 만료: {}", expiresAt);
    }
}
