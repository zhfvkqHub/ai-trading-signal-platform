# signal-service 구현 계획

> 소비 토픽: `raw.market`, `raw.news`, `raw.after-hours`
>
> 발행 토픽: `signal.detected`, `signal.rejected`
>
> 외부 의존: Redis (상태 캐싱 / dedup / cooldown)

---

## 구현 순서 Overview

```
Phase 1. 프로젝트 셋업 및 공통 모델
Phase 2. Kafka Consumer 설정
Phase 3. Scanner 모듈 (원시 조건 탐지)
Phase 4. Scorer 모듈 (가중 점수 계산)
Phase 5. Validator 모듈 (Redis 정책 검증)
Phase 6. Kafka 발행 (Signal Publisher)
Phase 7. 파이프라인 통합
Phase 8. 운영성
```

---

## Phase 1. 프로젝트 셋업 및 공통 모델

### 1-1. 프로젝트 Scaffolding

- [ ] `build.gradle.kts` — Spring Boot 3.5 + Kafka + Redis + Resilience4j
- [ ] `SignalServiceApplication.java` — `@SpringBootApplication`
- [ ] `settings.gradle` — `include 'services:signal-service'` 주석 해제
- [ ] `application.yml` — Kafka Consumer, Redis, 스캐너/스코어러/검증 설정

### 1-2. 소비 이벤트 모델 (`model/event/`)

collector-service의 `raw.*` 이벤트를 역직렬화하기 위한 DTO.

- [ ] `RawMarketEvent` — 장중 시세 (stockCode, price, volume, prevClosePrice, openPrice, …)
- [ ] `RawAfterHoursEvent` — 시간외 (stockCode, buyOrderVolume, sellOrderVolume, prevClosePrice, …)
- [ ] `RawNewsEvent` — 공시 (stockCode, corpName, reportName, receiptNo, …)

### 1-3. 발행 이벤트 모델 (`model/event/`)

- [ ] `SignalDetectedEvent` — 유효 신호 발행 이벤트
    - stockCode, stockName, signalType, score, reasons[], detectedAt, traceId
- [ ] `SignalRejectedEvent` — 필터링 신호 발행 이벤트
    - stockCode, signalType, score, rejectionReason, detectedAt, traceId

### 1-4. 내부 모델 (`model/`)

- [ ] `SignalType` enum — `VOLUME_SURGE`, `NEWS_SURGE`, `AFTER_HOURS_SURGE`, `GAP_UP`
- [ ] `ScanResult` — 스캐너 탐지 결과 (signalType, stockCode, rawValue, threshold, detected)
- [ ] `ScoredSignal` — 점수 산출 결과 (stockCode, score, reasons[], passed)
- [ ] `SignalReason` — 개별 신호 사유 (signalType, score, rawValue, threshold)
- [ ] `RejectionType` enum — `BELOW_THRESHOLD`, `DUPLICATE`, `COOLDOWN`, `BURST_LIMIT`

### 1-5. 설정 Properties

- [ ] `SignalScannerProperties` — 스캐너별 임계값 설정 (`@ConfigurationProperties(prefix = "signal.scanner")`)
    - `volume-surge.threshold` (기본 3.0배)
    - `news-surge.window-minutes` (기본 60분)
    - `news-surge.threshold` (기본 3건)
    - `after-hours.threshold` (기본 100,000주)
    - `gap-up.threshold` (기본 3.0%)
- [ ] `SignalScorerProperties` — 점수 가중치 및 최소 점수 (`@ConfigurationProperties(prefix = "signal.scorer")`)
    - `min-score` (기본 60)
    - `weights.volume-surge` (기본 35)
    - `weights.news-surge` (기본 25)
    - `weights.after-hours-surge` (기본 25)
    - `weights.gap-up` (기본 15)
    - `combo-bonus` (기본 10) — 2개 이상 동시 탐지 시 추가 점수
- [ ] `SignalValidatorProperties` — Redis 정책 설정 (`@ConfigurationProperties(prefix = "signal.validator")`)
    - `dedup-ttl-minutes` (기본 30)
    - `cooldown-minutes` (기본 10)
    - `burst-limit` (기본 10/분)
- [ ] `KafkaTopicProperties` — 토픽명 설정 (`@ConfigurationProperties(prefix = "kafka.topics")`)

---

## Phase 2. Kafka Consumer 설정

### 2-1. Consumer 설정 (`config/`)

- [ ] `KafkaConsumerConfig` — Consumer Factory 설정
    - `groupId`: `signal-service`
    - `auto-offset-reset`: `latest`
    - `key-deserializer`: StringDeserializer
    - `value-deserializer`: JsonDeserializer (trusted packages 설정)
    - `enable-auto-commit`: false (수동 ACK)
    - `concurrency`: 토픽별 파티션 수(3)에 맞춤
- [ ] DLQ 설정 — `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`
    - 3회 재시도 후 `raw.market.dlq`, `raw.news.dlq`, `raw.after-hours.dlq`로 전송

### 2-2. Redis 설정 (`config/`)

- [ ] `RedisConfig` — `RedisTemplate<String, String>` 빈 등록
- [ ] `application.yml`에 Redis 접속 정보 (`spring.data.redis.host/port`)

---

## Phase 3. Scanner 모듈 (원시 조건 탐지)

### 3-1. Scanner 공통 인터페이스

- [ ] `Scanner` 인터페이스 — `ScanResult scan(...)` 메서드 정의

### 3-2. `VolumeSurgeScanner` (`scanner/`)

- [ ] `raw.market` 이벤트 수신 시 호출
- [ ] Redis에서 종목별 전일 최종 거래량 조회 (`volume:daily:{stockCode}`)
- [ ] 현재 누적 거래량 / 전일 누적 거래량 비율 계산
- [ ] 비율 ≥ `threshold` → `ScanResult(VOLUME_SURGE, detected=true)` 반환
- [ ] 장 마감(15:30) 시점에 당일 최종 거래량을 Redis에 저장 (다음 날 비교용)
    - Key: `volume:daily:{stockCode}`, TTL: 48h
- [ ] 장 시작 직후 초기에는 거래량이 적으므로 최소 거래량 필터 적용

### 3-3. `NewsSurgeScanner` (`scanner/`)

- [ ] `raw.news` 이벤트 수신 시 호출
- [ ] Redis ZSET으로 종목별 뉴스 타임스탬프 기록 (`news:count:{stockCode}`)
    - score = epoch millis, member = receiptNo
    - TTL: window-minutes 이후 자동 만료
- [ ] 시간 윈도우 내 건수 조회 (`ZCOUNT`)
- [ ] 건수 ≥ `threshold` → `ScanResult(NEWS_SURGE, detected=true)` 반환

### 3-4. `AfterHoursSurgeScanner` (`scanner/`)

- [ ] `raw.after-hours` 이벤트 수신 시 호출
- [ ] `buyOrderVolume` ≥ `threshold` → `ScanResult(AFTER_HOURS_SURGE, detected=true)` 반환
- [ ] 이전 수집 시점 대비 증가율도 보조 지표로 기록
- [ ] 탐지 시 전일 종가를 Redis에 저장 (다음 날 갭상승 검증용)
    - Key: `gap:reference:{stockCode}`, TTL: 24h

### 3-5. `GapUpScanner` (`scanner/`)

- [ ] `raw.market` 이벤트 수신 시 호출 (장 개시 직후 시가 확정 시점)
- [ ] Redis에서 전일 종가 조회 (`gap:reference:{stockCode}`)
- [ ] `(openPrice - prevClosePrice) / prevClosePrice * 100` ≥ `threshold` → `ScanResult(GAP_UP, detected=true)`
- [ ] 하루 중 최초 1회만 판단 (Redis 플래그: `gap:checked:{stockCode}`, TTL: 당일)
- [ ] 보조 신호: 단독으로는 `signal.detected`를 발생시키지 않고 점수에만 기여

---

## Phase 4. Scorer 모듈 (가중 점수 계산)

### 4-1. `SignalScorer` (`scorer/`)

- [ ] `List<ScanResult>` → `ScoredSignal` 변환
- [ ] 점수 계산 로직:
    ```
    score = Σ (detected 조건 × weight)
    if (detected 조건 수 ≥ 2) score += comboBonus
    ```
- [ ] `ScoredSignal`에 `SignalReason[]` 포함 (각 조건의 기여 점수, 원시 수치, 임계값)
- [ ] `score < minScore` → `passed = false` (BELOW_THRESHOLD)

### 4-2. 가중치 기본값

| 조건 | 가중치 | 근거 |
|------|--------|------|
| VOLUME_SURGE | 35 | 가장 강한 선행 지표 |
| NEWS_SURGE | 25 | 촉매 역할 |
| AFTER_HOURS_SURGE | 25 | 다음 날 급등 선행 |
| GAP_UP | 15 | 확인 지표 (보조) |
| Combo Bonus | +10 | 2개 이상 동시 탐지 |

---

## Phase 5. Validator 모듈 (Redis 정책 검증)

### 5-1. `SignalValidator` (`validator/`)

- [ ] `ScoredSignal` → 통과 여부 판단
- [ ] 검증 순서:
    1. **Dedup** — `signal:dedup:{stockCode}:{signalType}` 키 존재 여부 확인
        - 존재 → reject (DUPLICATE)
        - 미존재 → SET + TTL(dedup-ttl-minutes)
    2. **Cooldown** — `signal:cooldown:{stockCode}` 키 존재 여부 확인
        - 존재 → reject (COOLDOWN)
        - 미존재 → SET + TTL(cooldown-minutes)
    3. **Burst Limit** — `signal:burst:{yyyyMMddHHmm}` INCR + TTL(60s)
        - 값 > burst-limit → reject (BURST_LIMIT)

### 5-2. `SignalRedisRepository` (`validator/`)

- [ ] Redis 연산 캡슐화
- [ ] `isDuplicate(stockCode, signalType)` → boolean
- [ ] `markAsSent(stockCode, signalType)` — dedup + cooldown 키 설정
- [ ] `incrementBurst()` → 현재 카운트 반환
- [ ] `cacheVolume(stockCode, volume)` / `getVolume(stockCode)` — 전일 거래량
- [ ] `cacheGapReference(stockCode, prevClosePrice)` / `getGapReference(stockCode)` — 갭상승용

---

## Phase 6. Kafka 발행 (Signal Publisher)

### 6-1. `SignalEventPublisher` (`kafka/`)

- [ ] `KafkaTemplate<String, Object>` 기반 발행
- [ ] `publishDetected(SignalDetectedEvent)` → `signal.detected` (key: stockCode)
- [ ] `publishRejected(SignalRejectedEvent)` → `signal.rejected` (key: stockCode)
- [ ] `traceId` 헤더 전파 (MDC → Kafka Header)
- [ ] `whenComplete` 콜백 로깅 (발행 성공/실패)

### 6-2. Kafka Producer 설정

- [ ] `application.yml` — Producer 설정 (acks: all, idempotence, retries)
- [ ] JSON Serializer 설정

---

## Phase 7. 파이프라인 통합

### 7-1. `RawEventListener` (`kafka/`)

전체 파이프라인을 연결하는 Kafka Listener.

- [ ] `@KafkaListener(topics = "raw.market", groupId = "signal-service")`
    - `RawMarketEvent` 수신
    - `VolumeSurgeScanner.scan()` + `GapUpScanner.scan()`
    - 탐지 결과가 있으면 → `SignalScorer.score()` → `SignalValidator.validate()` → `SignalEventPublisher.publish()`
- [ ] `@KafkaListener(topics = "raw.news", groupId = "signal-service")`
    - `RawNewsEvent` 수신
    - `NewsSurgeScanner.scan()`
    - 탐지 결과가 있으면 → 동일 파이프라인
- [ ] `@KafkaListener(topics = "raw.after-hours", groupId = "signal-service")`
    - `RawAfterHoursEvent` 수신
    - `AfterHoursSurgeScanner.scan()`
    - 탐지 결과가 있으면 → 동일 파이프라인

### 7-2. `SignalPipeline` (`pipeline/`)

파이프라인 오케스트레이션 서비스.

- [ ] `process(List<ScanResult> results, String stockCode, String traceId)` 메서드
    - Scanner 탐지 결과 → Scorer → Validator → Publisher 순차 실행
    - 각 단계 로깅 (traceId 포함)

---

## Phase 8. 운영성

### 8-1. 구조화 로그

- [ ] `logback-spring.xml` — JSON 포맷 출력 (Logstash encoder)
- [ ] 로그 패턴에 `traceId` 포함 (MDC 전파: Kafka Header → MDC)
- [ ] 주요 이벤트 로깅:
    - `SCAN_DETECTED` — 조건 탐지 시
    - `SIGNAL_SCORED` — 점수 산출 시
    - `SIGNAL_PUBLISHED` — 신호 발행 시
    - `SIGNAL_REJECTED` — 신호 거부 시 (사유 포함)

### 8-2. Health Check

- [ ] `/actuator/health` — Spring Actuator (show-details: always)
- [ ] Kafka Consumer 연결 상태 확인
- [ ] Redis 연결 상태 확인 (Spring Data Redis Auto-configuration)

### 8-3. 메트릭 (추후)

- [ ] 토픽별 소비 건수 카운터
- [ ] 조건별 탐지 건수 카운터
- [ ] 신호 발행/거부 건수 카운터
- [ ] 파이프라인 처리 시간 히스토그램

---

## 패키지 구조 최종안

```
com.signal.signalservice
├── SignalServiceApplication.java
├── config/
│   ├── KafkaConsumerConfig.java
│   ├── RedisConfig.java
│   ├── SignalScannerProperties.java
│   ├── SignalScorerProperties.java
│   ├── SignalValidatorProperties.java
│   └── KafkaTopicProperties.java
├── kafka/
│   ├── RawEventListener.java
│   └── SignalEventPublisher.java
├── scanner/
│   ├── Scanner.java                    (인터페이스)
│   ├── VolumeSurgeScanner.java
│   ├── NewsSurgeScanner.java
│   ├── AfterHoursSurgeScanner.java
│   └── GapUpScanner.java
├── scorer/
│   └── SignalScorer.java
├── validator/
│   ├── SignalValidator.java
│   └── SignalRedisRepository.java
├── pipeline/
│   └── SignalPipeline.java
└── model/
    ├── SignalType.java                 (enum)
    ├── RejectionType.java              (enum)
    ├── ScanResult.java                 (record)
    ├── ScoredSignal.java               (record)
    ├── SignalReason.java               (record)
    └── event/
        ├── RawMarketEvent.java         (소비용 DTO)
        ├── RawAfterHoursEvent.java     (소비용 DTO)
        ├── RawNewsEvent.java           (소비용 DTO)
        ├── SignalDetectedEvent.java    (발행용 DTO)
        └── SignalRejectedEvent.java    (발행용 DTO)
```

---

## 환경변수 / 시크릿 목록

| 키 | 설명 | 기본값 |
|----|------|--------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 접속 주소 | `localhost:29092` |
| `REDIS_HOST` | Redis 접속 주소 | `localhost` |
| `REDIS_PORT` | Redis 접속 포트 | `6379` |

> signal-service는 외부 API를 직접 호출하지 않으므로 API 키가 불필요합니다.

---

## 남은 작업 (추후)

- [ ] AI 모델 연동 — 과거 신호 성공/실패 이력 학습 기반 점수 보정
- [ ] 조건별 가중치 동적 조정 (strategy_config 테이블 연동)
- [ ] Circuit Breaker 적용 (Redis 장애 시 fallback)
- [ ] 메트릭 (Micrometer + Prometheus)
- [ ] 통합 테스트 (Embedded Kafka + Embedded Redis)

---

## 참고 링크

- collector-service 이벤트 모델: `services/collector-service/src/main/java/com/signal/collectorservice/model/raw/`
- Kafka 토픽 생성 스크립트: `scripts/kafka/create-topics.sh`
- 플랫폼 전체 아키텍처: `README.md`
