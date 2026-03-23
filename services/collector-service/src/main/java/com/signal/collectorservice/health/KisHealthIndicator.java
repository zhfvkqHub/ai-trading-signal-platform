package com.signal.collectorservice.health;

import com.signal.collectorservice.client.kis.auth.KisTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisHealthIndicator implements HealthIndicator {

    private final KisTokenManager tokenManager;

    @Override
    public Health health() {
        try {
            String token = tokenManager.getAccessToken();
            if (token != null && !token.isBlank()) {
                return Health.up()
                        .withDetail("service", "KIS OpenAPI")
                        .withDetail("tokenStatus", "valid")
                        .build();
            }
            return Health.down()
                    .withDetail("service", "KIS OpenAPI")
                    .withDetail("tokenStatus", "empty")
                    .build();
        } catch (Exception e) {
            log.warn("KIS 헬스 체크 실패", e);
            return Health.down()
                    .withDetail("service", "KIS OpenAPI")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
