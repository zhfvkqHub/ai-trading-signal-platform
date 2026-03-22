package com.signal.collectorservice.config.feign;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KisErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("KIS API 오류 [{}] status={}", methodKey, response.status());

        return switch (response.status()) {
            case 429 -> new KisRateLimitException("KIS API Rate Limit 초과: " + methodKey);
            case 401 -> new KisUnauthorizedException("KIS API 인증 실패 (토큰 만료 가능): " + methodKey);
            case 500, 503 -> new KisServerException(
                    "KIS API 서버 오류 [" + response.status() + "]: " + methodKey);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    public static class KisRateLimitException extends RuntimeException {
        public KisRateLimitException(String message) {
            super(message);
        }
    }

    public static class KisUnauthorizedException extends RuntimeException {
        public KisUnauthorizedException(String message) {
            super(message);
        }
    }

    public static class KisServerException extends RuntimeException {
        public KisServerException(String message) {
            super(message);
        }
    }
}
