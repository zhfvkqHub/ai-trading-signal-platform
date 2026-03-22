package com.signal.collectorservice.client.kis.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,    // 초 단위 (86400 = 24h)
        @JsonProperty("access_token_token_expired") String accessTokenExpired
) {
}
