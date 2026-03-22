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
Phase 1. 기반 인프라
Phase 2. 장 시간 관리
Phase 3. KIS API 클라이언트
Phase 4. DART API 클라이언트
Phase 5. 각 수집기 구현
Phase 6. Kafka 발행
Phase 7. 운영성
```

---

## Phase 1. 기반 인프라 설정

### 1-1. 의존성 추가 (`build.gradle.kts`)

- [ ] `spring-boot-starter-webflux` — 비동기 HTTP 클라이언트 (WebClient)
- [ ] `spring-retry` — API 재시도 처리
- [ ] `resilience4j` — Rate Limit / Circuit Breaker
- [ ] `jackson-module-kotlin` or `jackson-databind` 확인

### 1-2. `application.yml` 환경 설정

- [ ] KIS API 설정 (`appKey`, `appSecret`, base-url)
- [ ] DART API 설정 (`apiKey`, base-url)
- [ ] Kafka 설정 (broker, producer 설정)
- [ ] 스케줄 인터벌 설정 (market: 30s, after-hours: 5m, news: 10m)
- [ ] Rate Limit 설정값 (KIS: 20req/s, DART: 1req/s)

### 1-3. 공통 모델 정의 (`model/`)

- [ ] `RawMarketEvent` — 장중 시세 이벤트
- [ ] `RawAfterHoursEvent` — 시간외 단일가 이벤트
- [ ] `RawNewsEvent` — 공시/뉴스 이벤트
- [ ] `DataSource` enum — KIS, DART, etc.
- [ ] `TradingSession` enum — PRE_MARKET, REGULAR, AFTER_HOURS, CLOSED

---

## Phase 2. 장 시간 관리

### 2-1. `TradingSessionManager`

- [ ] 영업일 판단 (공휴일 제외) — `java.time` + 한국 공휴일 리스트
- [ ] 현재 세션 반환 (`TradingSession`)
    - `PRE_MARKET` : 08:00 ~ 09:00
    - `REGULAR` : 09:00 ~ 15:30
    - `AFTER_HOURS` : 15:30 ~ 18:00
    - `CLOSED` : 그 외 (수집 차단)
- [ ] 세션별 수집 활성화 여부 판단 메서드

---

## Phase 3. KIS API 클라이언트

> 공식 문서: https://apiportal.koreainvestment.com

### 3-1. 인증 (`kis/auth/`)

- [ ] `KisAuthClient` — OAuth 2.0 Access Token 발급
    - POST `/oauth2/tokenP`
    - `appKey` + `appSecret` → `access_token` (유효기간 24h)
- [ ] `KisTokenManager` — 토큰 캐싱 + 만료 전 자동 갱신

### 3-2. 장중 시세 조회 (`kis/market/`)

- [ ] `KisMarketClient`
    - GET `/uapi/domestic-stock/v1/quotations/inquire-price`
    - 응답 → `RawMarketEvent` 매핑
    - 수집 항목: 현재가, 시가, 고가, 저가, 거래량, 전일 종가, 수집 시각

### 3-3. 시간외 단일가 조회 (`kis/afterhours/`)

- [ ] `KisAfterHoursClient`
    - GET `/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn` (시간외 호가)
    - 응답 → `RawAfterHoursEvent` 매핑
    - 수집 항목: 시간외 단일가, 시간외 거래량, 매수/매도 대기 물량

### 3-4. Rate Limit 처리

- [ ] KIS API 제한: **초당 20건** (TR별 상이)
- [ ] `RateLimiter` 적용 (Resilience4j)
- [ ] 429 응답 시 backoff + retry 처리

---

## Phase 4. DART API 클라이언트

> 공식 문서: https://opendart.fss.or.kr

### 4-1. 공시 목록 조회 (`dart/`)

- [ ] `DartDisclosureClient`
    - GET `/api/list.json`
    - 파라미터: `bgn_de` (조회 시작일), `pblntf_ty` (공시 유형)
    - 응답 → `RawNewsEvent` 매핑
    - 수집 항목: 공시 제목, 종목명, 종목코드, 공시 유형, 접수 일시, 공시 URL

### 4-2. 중복 방지

- [ ] 마지막 수집 공시 `rcept_no` 기준으로 신규 건만 발행
- [ ] In-memory Set 또는 Redis (추후)로 발행 여부 추적

### 4-3. Rate Limit 처리

- [ ] DART API 제한: **분당 1,000건** (실질적으로 1req/s 권장)
- [ ] `RateLimiter` 적용

---

## Phase 5. 수집기 구현

### 5-1. `MarketDataCollector`

- [ ] `@Scheduled` — 장중(REGULAR) 세션에서만 동작, 30초 간격
- [ ] 수집 대상 종목 리스트 관리 (초기: 코스피 200 or 설정값)
- [ ] 종목별 KIS 시세 조회 → `RawMarketEvent` 생성 → Kafka 발행

### 5-2. `AfterHoursCollector`

- [ ] `@Scheduled` — AFTER_HOURS 세션에서만 동작, 5분 간격
- [ ] 종목별 KIS 시간외 호가 조회 → `RawAfterHoursEvent` 생성 → Kafka 발행

### 5-3. `NewsCollector`

- [ ] `@Scheduled` — 항상 동작 (공휴일 포함 뉴스 발생 가능), 10분 간격
- [ ] DART 공시 조회 → 신규 건 필터링 → `RawNewsEvent` 생성 → Kafka 발행

---

## Phase 6. Kafka 발행

### 6-1. `RawEventPublisher`

- [ ] `KafkaTemplate<String, Object>` 공통 발행 메서드
- [ ] 토픽별 발행:
    - `raw.market` — key: `stockCode`
    - `raw.after-hours` — key: `stockCode`
    - `raw.news` — key: `stockCode` (없으면 `ALL`)
- [ ] `traceId` 헤더 주입 (MDC 연계)
- [ ] 발행 실패 시 로그 + Dead Letter Queue (DLQ) 고려

### 6-2. Kafka Producer 설정

- [ ] `acks=all` — 메시지 유실 방지
- [ ] `retries=3`
- [ ] `idempotence=true`
- [ ] 직렬화: JSON (`JsonSerializer`)

---

## Phase 7. 운영성

### 7-1. 구조화 로그

- [ ] `logback-spring.xml` — JSON 포맷 출력 (Logstash encoder)
- [ ] MDC에 `traceId`, `stockCode`, `session` 포함
- [ ] 수집 성공/실패 이벤트 로깅

### 7-2. Health Check

- [ ] `/actuator/health` — Spring Actuator 기본 제공
- [ ] KIS API 연결 상태 커스텀 HealthIndicator
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
│   ├── KafkaProducerConfig.java
│   ├── WebClientConfig.java
│   └── ResilienceConfig.java
├── schedule/
│   └── TradingSessionManager.java
├── collector/
│   ├── market/
│   │   ├── MarketDataCollector.java
│   │   └── MarketDataNormalizer.java
│   ├── afterhours/
│   │   ├── AfterHoursCollector.java
│   │   └── AfterHoursNormalizer.java
│   └── news/
│       ├── NewsCollector.java
│       └── NewsNormalizer.java
├── client/
│   ├── kis/
│   │   ├── auth/
│   │   │   ├── KisAuthClient.java
│   │   │   └── KisTokenManager.java
│   │   ├── market/
│   │   │   └── KisMarketClient.java
│   │   └── afterhours/
│   │       └── KisAfterHoursClient.java
│   └── dart/
│       └── DartDisclosureClient.java
├── kafka/
│   └── RawEventPublisher.java
└── model/
    ├── raw/
    │   ├── RawMarketEvent.java
    │   ├── RawAfterHoursEvent.java
    │   └── RawNewsEvent.java
    ├── TradingSession.java
    └── DataSource.java
```

---

## 환경변수 / 시크릿 목록

| 키                         | 설명                   |
|---------------------------|----------------------|
| `KIS_APP_KEY`             | KIS Developers 앱 키   |
| `KIS_APP_SECRET`          | KIS Developers 앱 시크릿 |
| `KIS_ACCOUNT_NO`          | 계좌번호 (모의투자 or 실전)    |
| `DART_API_KEY`            | DART OpenAPI 인증키     |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소         |

---

## 참고 링크

- KIS Developers: https://apiportal.koreainvestment.com
- DART OpenAPI: https://opendart.fss.or.kr
- KIS API GitHub 예제: https://github.com/koreainvestment/open-trading-api
