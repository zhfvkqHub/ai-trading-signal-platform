# collector-service 구현 계획

> 외부 API: `KIS Developers (한국투자증권)` 및 `DART OpenAPI (금융감독원)`
>
> - 실시간 시세: KIS API — REST 및 WebSocket 제공. API 키 발급 및 계좌(모의투자 가능) 필요.
> - 공시/뉴스: DART API — 공식 공시 데이터 제공. API 키 발급만 필요.
>
> 발행 토픽: `raw.market`, `raw.news`, `raw.after-hours`

---

## 구현 순서 Overview

```
Phase 1. 공통 모델 정의
Phase 2. 장 시간 관리
Phase 3. KIS API 클라이언트
Phase 4. DART API 클라이언트
Phase 5. 각 수집기 구현
Phase 6. Kafka 발행
Phase 7. 운영성
```

---

## Phase 1. 기반 인프라

### 공통 모델 정의 (`model/`)

- [x] `RawMarketEvent` — 장중 시세 이벤트
- [x] `RawAfterHoursEvent` — 시간외 단일가 이벤트
- [x] `RawNewsEvent` — 공시/뉴스 이벤트
- [x] `DataSource` enum — KIS, DART
- [x] `TradingSession` enum — PRE_MARKET, REGULAR, AFTER_HOURS, CLOSED

---

## Phase 3. KIS API 클라이언트

> 공식 문서: https://apiportal.koreainvestment.com

### 3-1. 인증 (`client/kis/auth/`)

- [x] `KisAuthFeignClient` — OAuth 2.0 Access Token 발급
    - POST `/oauth2/tokenP`
    - `appKey` + `appSecret` → `access_token` (유효기간 24h)
- [x] `KisTokenResponse` — 토큰 응답 매핑
- [x] `KisTokenManager` — 토큰 캐싱 + 만료 10분 전 자동 갱신 + `@Retryable`

### 3-2. Feign 공통 설정 (`config/feign/`)

- [x] `KisApiConfig` — 인증이 필요한 KIS 클라이언트에 적용되는 Feign 설정
    - `KisRequestInterceptor`, `KisErrorDecoder`, `Retryer`, Logger Level
- [x] `KisRequestInterceptor` — 공통 헤더 주입 (`authorization`, `appkey`, `appsecret`, `custtype`)
- [x] `KisErrorDecoder` — 상태코드별 예외 변환 (429, 401, 500/503)

### 3-3. 장중 시세 조회 (`client/kis/market/`)

- [x] `KisMarketFeignClient`
    - GET `/uapi/domestic-stock/v1/quotations/inquire-price`
    - 수집 항목: 현재가, 시가, 고가, 저가, 전일 종가, 누적 거래량, 누적 거래대금, 종목명
- [x] `KisMarketResponse` — API 응답 매핑

### 3-4. 시간외 단일가 조회 (`client/kis/afterhours/`)

- [x] `KisAfterHoursFeignClient`
  - GET `/uapi/domestic-stock/v1/quotations/inquire-overtime-price`
    - 수집 항목: 시간외 현재가, 시간외 거래량/거래대금, 상·하한가, 당일 종가, 순매수 수량, 예상 체결량
- [x] `KisAfterHoursResponse` — API 응답 매핑

---

## Phase 4. DART API 클라이언트

> 공식 문서: https://opendart.fss.or.kr

### 4-1. Feign 클라이언트 (`client/dart/`)

- [x] `DartDisclosureFeignClient`
    - GET `/api/list.json`
    - 파라미터: `bgn_de` (조회 시작일), `page_no`, `page_count`
- [x] `DartDisclosureResponse` — API 응답 매핑
- [x] 응답 → `RawNewsEvent` 매핑

### 4-2. DART Feign 설정

- [x] `DartApiConfig` — DART 전용 Feign 설정 (Retryer 200ms~2s, 3회)
- [x] `DartRequestInterceptor` — `crtfc_key` 쿼리 파라미터 주입
- [x] `DartErrorDecoder` — DART API 오류 처리 (429→RateLimitException, 5xx→ServerException)

### 4-3. 중복 방지

- [x] 마지막 수집 공시 `rcept_no` 기준으로 신규 건만 발행 (`volatile lastReceiptNo`)
- [ ] Redis 기반 발행 여부 추적 (추후)

### 4-4. Rate Limit 처리

- [x] DART API 제한 설정: 1req/s 권장
- [ ] `RateLimiter` 적용 (Resilience4j)

---

## Phase 5. 수집기 구현

### 5-1. `MarketDataCollector`

- [x] `@Scheduled(cron)` — 장중(REGULAR) 세션에서만 동작, 30초 간격
- [x] `TradingSessionManager` 연동하여 CLOSED 시 수집 스킵
- [x] 종목별 `KisMarketFeignClient` 시세 조회 → `RawMarketEvent` 변환 → Kafka 발행

### 5-2. `AfterHoursCollector`

- [x] `@Scheduled(cron)` — AFTER_HOURS 세션에서만 동작, 5분 간격
- [x] 종목별 `KisAfterHoursFeignClient` 시간외 호가 조회 → `RawAfterHoursEvent` 변환 → Kafka 발행

### 5-3. `NewsCollector`

- [x] `@Scheduled(cron)` — 영업일에 동작, 10분 간격
- [x] `DartDisclosureFeignClient` 공시 조회 → 신규 건 필터링 → `RawNewsEvent` 변환 → Kafka 발행

---

## Phase 6. Kafka 발행

### 6-1. `RawEventPublisher`

- [x] `KafkaTemplate<String, Object>` 공통 발행 메서드
- [x] 토픽별 발행:
    - `raw.market` — key: `stockCode`
    - `raw.after-hours` — key: `stockCode`
    - `raw.news` — key: `receiptNo`
- [x] `traceId` 헤더 주입 (MDC 연계)
- [x] 발행 성공/실패 시 `whenComplete` 콜백 로깅
- [ ] Dead Letter Queue (DLQ) 설정 (추후)

---

## Phase 7. 운영성

### 7-1. 구조화 로그

- [x] `logback-spring.xml` — JSON 포맷 출력 (Logstash encoder)
- [x] 로그 패턴에 `traceId` 포함 (MDC)
- [x] 수집 성공/실패 이벤트 로깅

### 7-2. Health Check

- [x] `/actuator/health` — Spring Actuator 기본 제공 (`show-details: always`)
- [x] `KisHealthIndicator` — KIS API 토큰 유효성 기반 커스텀 HealthIndicator
- [ ] Kafka Producer 연결 상태 확인

### 7-3. 메트릭 (추후)

- [ ] 토픽별 발행 건수 카운터
- [ ] API 응답 시간 히스토그램
- [ ] Rate Limit 초과 횟수 카운터

---

## 패키지 구조 최종안

```
com.signal.collectorservice
├── CollectorServiceApplication.java
├── config/
│   ├── AppConfig.java
│   ├── KisProperties.java
│   ├── DartProperties.java
│   ├── CollectorProperties.java
│   ├── TradingSessionProperties.java
│   ├── KafkaTopicProperties.java
│   └── feign/
│       ├── KisApiConfig.java
│       ├── KisRequestInterceptor.java
│       ├── KisErrorDecoder.java
│       ├── DartApiConfig.java
│       ├── DartRequestInterceptor.java
│       └── DartErrorDecoder.java
├── schedule/
│   └── TradingSessionManager.java
├── collector/
│   ├── market/
│   │   └── MarketDataCollector.java
│   ├── afterhours/
│   │   └── AfterHoursCollector.java
│   └── news/
│       └── NewsCollector.java
├── client/
│   ├── kis/
│   │   ├── auth/
│   │   │   ├── KisAuthFeignClient.java
│   │   │   ├── KisTokenManager.java
│   │   │   └── KisTokenResponse.java
│   │   ├── market/
│   │   │   ├── KisMarketFeignClient.java
│   │   │   └── KisMarketResponse.java
│   │   └── afterhours/
│   │       ├── KisAfterHoursFeignClient.java
│   │       └── KisAfterHoursResponse.java
│   └── dart/
│       ├── DartDisclosureFeignClient.java
│       └── DartDisclosureResponse.java
├── kafka/
│   └── RawEventPublisher.java
├── health/
│   └── KisHealthIndicator.java
└── model/
    ├── raw/
    │   ├── RawMarketEvent.java
    │   ├── RawAfterHoursEvent.java
    │   └── RawNewsEvent.java
    ├── TradingSession.java
    └── DataSource.java
```

## 참고 링크

- KIS Developers: https://apiportal.koreainvestment.com
- DART OpenAPI: https://opendart.fss.or.kr
- KIS API GitHub 예제: https://github.com/koreainvestment/open-trading-api
