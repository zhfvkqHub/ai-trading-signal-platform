package com.signal.collectorservice.config.feign;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DartErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("DART API 오류 [{}] status={}", methodKey, response.status());

        return switch (response.status()) {
            case 429 -> new DartRateLimitException("DART API Rate Limit 초과: " + methodKey);
            case 500, 502, 503 -> new DartServerException(
                    "DART API 서버 오류 [" + response.status() + "]: " + methodKey);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    public static class DartRateLimitException extends RuntimeException {
        public DartRateLimitException(String message) {
            super(message);
        }
    }

    public static class DartServerException extends RuntimeException {
        public DartServerException(String message) {
            super(message);
        }
    }
}
