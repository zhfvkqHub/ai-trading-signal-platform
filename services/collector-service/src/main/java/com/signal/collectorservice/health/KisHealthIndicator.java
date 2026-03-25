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
        if (tokenManager.isCachedTokenValid()) {
            return Health.up()
                    .withDetail("service", "KIS OpenAPI")
                    .withDetail("tokenStatus", "valid")
                    .build();
        }
        return Health.unknown()
                .withDetail("service", "KIS OpenAPI")
                .withDetail("tokenStatus", "not yet acquired or expired")
                .build();
    }
}
