# AI Trading Signal Platform

실시간 주식 시장 데이터를 분석하여 **거래 신호를 탐지하는 이벤트 기반 AI 플랫폼**

---
## Overview

본 시스템은 시장 데이터와 뉴스 데이터를 실시간으로 수집·분석하여 **거래 신호를 생성하고 Kafka 기반 이벤트로 전달하는 구조**를 갖습니다.

초기 단계에서는 자동 매매를 수행하지 않고,

> **신호 탐지 → AI 검증 → 알림 → 이력 저장 → Shadow Trading**

흐름을 통해 **신호의 유효성을 검증하고 모델을 고도화**하는 데 집중합니다.

---
## Signal Detection Logic

다음 4가지 조건을 AI가 분석하여 거래 신호를 생성합니다.

| # | 신호 | 설명                              |
|---|------|---------------------------------|
| 1 | **거래량 급등** | 전일 대비 거래량이 급격히 증가한 종목 탐지        |
| 2 | **뉴스 급증** | 단시간 내 관련 뉴스·공시가 대거 발생한 종목 탐지    |
| 3 | **거래대기 물량 급증** | 장 마감 이후 시간외 매수 대기 잔량이 급증한 종목 탐지 |
| 4 | **갭상승 확인** | 참고 지표(보조 신호)                    |

신호는 특정 시점에 제한되지 않고, 조건이 충족되는 즉시 이벤트 기반으로 생성 및 알림이 발송됩니다.

향후 AI 모델이 과거 신호의 성공/실패 이력을 학습해 정확도를 고도화합니다.

---
## Architecture

```
[External Data Sources]
  ├─ Market Data        (가격, 거래량)
  ├─ News / Disclosure  (뉴스, 공시)
  └─ After-hours Data   (시간외 거래대기 물량, 갭 데이터)
          │
          ▼
[collector-service]
  ├─ 데이터 수집 및 최소 정규화
  ├─ 영업일 / 장중·장외 시간 판단
  └─ Kafka raw 이벤트 발행
          │
          ▼
        Kafka (Raw Topics)
  ├─ raw.market         ← 가격 / 거래량
  ├─ raw.news           ← 뉴스 / 공시
  └─ raw.after-hours    ← 시간외 거래대기 물량 / 갭 데이터
          │
          ▼
[signal-service]  ← 핵심 서비스
  ├─ [scanner]    원시 조건 탐지 (거래량 / 뉴스 / 거래대기 / 갭상승)
  ├─ [scorer]     가중 점수 계산 및 신호 강도 산출
  └─ [validator]  Redis dedup / TTL / 정책 필터링
          │
          ▼
        Kafka (Signal Topics)
  ├─ signal.detected
  ├─ signal.rejected
  ├─ signal.detected.dlq   ← 처리 실패 메시지 보관
  └─ raw.*.dlq             ← raw 토픽 처리 실패 메시지 보관
          │
   ┌──────┼──────────────────┐
   ▼      ▼                  ▼
[notification]  [history]  [trade-planning]
  알림 발송       이력 저장    Shadow Trading
  Telegram/Slack  MySQL       (실제 주문 없음)

   Redis (dedup / TTL / cache / cooldown)
```

---
## Tech Stack

| 분류 | 기술 |
|------|------|
| Backend | Java, Spring Boot |
| Messaging | Apache Kafka |
| Cache / State | Redis |
| Database | MySQL |
| Infrastructure | Docker Compose |
| AI / 분석 | Python (Claude API 또는 자체 ML 모델) |
| 모니터링 | Prometheus + Grafana |
| 로깅 | 구조화 로그(JSON) + ELK Stack |

---
## Services

### 1. collector-service
- 시장 데이터 / 뉴스 / 시간외 거래대기 물량 수집
- Kafka `raw.*` 토픽으로 이벤트 발행

### 2. signal-service
내부를 3개 모듈로 분리하여 단일 장애 지점(SPOF) 위험 최소화

| 모듈 | 역할 |
|------|------|
| scanner | 4가지 원시 조건 탐지 담당 |
| scorer | 조건별 가중 점수 계산 및 신호 강도 산출 |
| validator | Redis dedup / cooldown / TTL / 정책 검증 |

### 3. notification-service
- `signal.detected` 토픽 구독
- 신호 발생 즉시 Telegram / Slack 알림 전송

### 4. history-service
- 신호 / 알림 / 검증 결과 이력 저장 (MySQL)
- AI 학습용 데이터 축적

### 5. trade-planning-service
- 실제 주문 없이 Shadow Trading 기반 계획 생성
- 향후 자동매매 확장 대비 인터페이스 구현

---
## Redis Usage

| 용도 | 설명 |
|------|------|
| Dedup | 동일 종목 중복 신호 방지 |
| Cooldown | 종목별 신호 발송 간격 제어 |
| Burst Count | 단위 시간 내 이벤트 수 집계 |
| State Cache | 최근 시장 상태 및 점수 캐싱 |
| Gap Reference | 전날 신호 종목 갭상승 교차 검증용 임시 저장 |

---
## Database Schema (MySQL)

| 테이블 | 설명 |
|--------|------|
| `signals` | 생성된 신호 이력 |
| `signal_reasons` | 신호 발생 근거 (조건별 기여 점수 포함) |
| `validation_results` | AI 검증 결과 |
| `alert_history` | 알림 발송 이력 |
| `news_metadata` | 뉴스 / 공시 메타데이터 |
| `trade_plans` | Shadow Trading 계획 |
| `strategy_config` | 전략 파라미터 설정 |

### signal_reasons 상세 스키마

```sql
signal_reasons
  ├─ signal_id     -- FK → signals
  ├─ reason_type   -- VOLUME_SURGE | NEWS_SURGE | AFTER_HOURS | GAP_UP
  ├─ score         -- 이 조건이 기여한 점수
  ├─ raw_value     -- 실제 수치 (거래량 배율, 뉴스 건수 등)
  └─ threshold     -- 탐지 기준값 (AI 학습 시 피처로 활용)
```

---
## Kafka Topics

| 구분 | 토픽 | 설명 |
|------|------|------|
| Raw | `raw.market` | 가격 / 거래량 이벤트 |
| Raw | `raw.news` | 뉴스 / 공시 이벤트 |
| Raw | `raw.after-hours` | 시간외 거래대기 물량 / 갭 데이터 |
| Signal | `signal.detected` | 탐지된 유효 신호 |
| Signal | `signal.rejected` | 필터링된 신호 |
| DLQ | `raw.*.dlq` | raw 토픽 처리 실패 메시지 |
| DLQ | `signal.detected.dlq` | 신호 처리 실패 메시지 |
| Future | `trade.plan` | 자동매매 계획 (예정) |
| Future | `audit.event` | 감사 로그 (예정) |

---
## Local Development

### 1. 인프라 실행

```bash
docker compose up -d
```

### 2. Kafka 토픽 생성

```bash
./scripts/kafka/create-topics.sh
```

---

## Author

- Backend Developer
- Event-driven Architecture / Kafka / AI Trading System
