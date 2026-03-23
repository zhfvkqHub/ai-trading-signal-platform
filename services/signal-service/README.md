# signal-service

`raw.*` 토픽의 원시 데이터를 기반으로 4가지 매매 신호를 탐지하고, 점수를 산출한 뒤, 중복/쿨다운 정책을 거쳐 `signal.detected` / `signal.rejected` 토픽으로 발행하는 서비스입니다.
## Overview

`signal-service`는 AI Trading Signal Platform의 핵심 분석 서비스입니다.

`collector-service`가 수집한 원시 데이터를 기반으로 사전 정의된 조건에 따라 매매 신호를 탐지하고 점수화하며 검증하는 파이프라인을 제공합니다.

내부는 3개 모듈로 구성되어 각 책임을 분리합니다.

| 모듈 | 책임 |
|------|------|
| **scanner** | `raw.*` 토픽 소비 → 4가지 원시 조건 탐지 |
| **scorer** | 탐지된 조건들의 가중 점수 계산 및 신호 강도 산출 |
| **validator** | Redis 기반 중복 제거 / 쿨다운 / 정책 필터링 |

---

## Responsibilities

### 1. 원시 조건 탐지 (Scanner)

| # | 신호 | 소비 토픽 | 탐지 기준 |
|---|------|----------|----------|
| 1 | **거래량 급등** | `raw.market` | 전일 대비 누적 거래량 비율이 임계값 초과 |
| 2 | **뉴스 급증** | `raw.news` | 단위 시간 내 종목 관련 공시 건수가 임계값 초과 |
| 3 | **거래대기 물량 급증** | `raw.after-hours` | 시간외 매수 대기 물량이 임계값 초과 |
| 4 | **갭상승 확인** | `raw.market` | 당일 시가 / 전일 종가 비율이 임계값 초과 (보조 신호) |

### 2. 가중 점수 계산 (Scorer)

- 탐지된 조건별 가중치 적용
- 조건 조합에 따른 보너스 점수
- 최종 신호 강도(score) 산출
- 임계 점수 미달 시 `signal.rejected`로 분류

### 3. 정책 검증 (Validator)

- **Dedup**: 동일 종목 + 동일 신호 유형 중복 방지 (Redis SET + TTL)
- **Cooldown**: 종목별 신호 발송 최소 간격 보장
- **Burst Count**: 단위 시간 내 전체 신호 발송 상한 제어
- 통과 시 `signal.detected`, 미통과 시 `signal.rejected`로 발행

### 4. Kafka 발행

- `signal.detected` — 유효 신호 (notification-service, history-service 소비)
- `signal.rejected` — 필터링된 신호 (history-service 소비, 분석용)

### 5. 상태 관리 (Redis)

- 종목별 전일 거래량 캐싱 (거래량 급등 비교 기준)
- 종목별 시간대 뉴스 카운트 집계
- 시간외 거래대기 물량 이전 값 저장
- 전일 종가 캐싱 (갭상승 교차 검증)

---

## Non-Responsibilities

`signal-service` 는 아래 작업을 수행하지 않습니다.

- 외부 API 직접 호출 (collector-service 역할)
- 알림 발송 (notification-service 역할)
- 이력 저장 / DB 쓰기 (history-service 역할)
- 실제 매매 주문 (trade-planning-service 역할)

---

## Architecture Role

```text
Kafka (Raw Topics)
  ├─ raw.market
  ├─ raw.news
  └─ raw.after-hours
          │
          ▼
[signal-service]
  ├─ [scanner]     @KafkaListener → 원시 조건 탐지
  │     ├─ VolumeSurgeScanner       (거래량 급등)
  │     ├─ NewsSurgeScanner         (뉴스 급증)
  │     ├─ AfterHoursSurgeScanner   (거래대기 물량 급증)
  │     └─ GapUpScanner             (갭상승 확인)
  │                │
  │          ScanResult[]
  │                │
  ├─ [scorer]      가중 점수 계산
  │     └─ SignalScorer
  │                │
  │          ScoredSignal (score ≥ threshold?)
  │                │
  ├─ [validator]   Redis 정책 검증
  │     └─ SignalValidator
  │                │
  │          ┌─── pass ───┐── reject ──┐
  │          ▼             ▼            │
  └─ [publisher]                       │
        ├─ signal.detected  ◄──────────┘
        └─ signal.rejected
                │
     ┌──────────┼──────────────────┐
     ▼          ▼                  ▼
[notification] [history]  [trade-planning]
```

---

## Data Flow

```
1. @KafkaListener 수신
   └─ raw.market / raw.news / raw.after-hours

2. Scanner 탐지
   └─ 조건 충족 시 ScanResult 생성
      ├─ signalType: VOLUME_SURGE | NEWS_SURGE | AFTER_HOURS_SURGE | GAP_UP
      ├─ stockCode, rawValue, threshold
      └─ traceId 전파

3. Scorer 점수 계산
   └─ ScanResult[] → ScoredSignal
      ├─ 개별 조건 점수 합산
      ├─ 조합 보너스 적용
      └─ score < minThreshold → rejected

4. Validator 정책 검증
   └─ Redis 기반
      ├─ dedup 확인 (동일 종목+유형 TTL 내 중복?)
      ├─ cooldown 확인 (종목별 최소 간격?)
      └─ burst 확인 (전체 신호 수 상한?)

5. Publisher 발행
   ├─ 통과 → signal.detected
   └─ 미통과 → signal.rejected (사유 포함)
```

---

## Key Events

### 소비 이벤트 (Input)

| 토픽 | 이벤트 | Key |
|------|--------|-----|
| `raw.market` | RawMarketEvent | stockCode |
| `raw.news` | RawNewsEvent | receiptNo |
| `raw.after-hours` | RawAfterHoursEvent | stockCode |

### 발행 이벤트 (Output)

| 토픽 | 이벤트 | Key | 설명 |
|------|--------|-----|------|
| `signal.detected` | SignalDetectedEvent | stockCode | 유효 신호 |
| `signal.rejected` | SignalRejectedEvent | stockCode | 필터링된 신호 + 사유 |

---

## Configuration

| 키 | 설명 | 기본값 |
|----|------|--------|
| `signal.scanner.volume-surge.threshold` | 거래량 급등 배율 임계값 | 3.0 |
| `signal.scanner.news-surge.window-minutes` | 뉴스 급증 판단 시간 윈도우 | 60 |
| `signal.scanner.news-surge.threshold` | 뉴스 급증 건수 임계값 | 3 |
| `signal.scanner.after-hours.threshold` | 거래대기 물량 급증 임계값 | 100000 |
| `signal.scanner.gap-up.threshold` | 갭상승률 임계값 (%) | 3.0 |
| `signal.scorer.min-score` | 신호 발행 최소 점수 | 60 |
| `signal.scorer.weights.*` | 조건별 가중치 | 조건별 상이 |
| `signal.validator.dedup-ttl-minutes` | 중복 방지 TTL | 30 |
| `signal.validator.cooldown-minutes` | 종목별 쿨다운 시간 | 10 |
| `signal.validator.burst-limit` | 분당 최대 신호 수 | 10 |

---

## Tech Stack

| 분류 | 기술 |
|------|------|
| Runtime | Java 21, Spring Boot 3.5 |
| Messaging | Spring Kafka (Consumer + Producer) |
| State / Cache | Spring Data Redis |
| Resilience | Resilience4j (Circuit Breaker) |
| Logging | Logstash Logback Encoder (JSON) |
| Test | JUnit 5, Spring Kafka Test, Embedded Redis |
