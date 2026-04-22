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
   ┌──────┘
   ▼
[notification-service]
  알림 발송 (Telegram / Slack)
  └─ Kafka notification.dispatched 발행 (SENT / SUPPRESSED 이력)
          │
   ┌──────┼───────────────────┐
   ▼      ▼                   ▼
[history-service]  [signal.detected]  [trade-planning]
  이력 저장 (MySQL)   (위에서 동시 구독)   Shadow Trading
  - signal_events                      (실제 주문 없음, 미구현)
  - notification_logs
  웹 대시보드 (:8084)

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
- Redis 기반 채널별 쿨다운 / dedup / 속도 제한 적용
- 발송 결과(SENT / SUPPRESSED)를 `notification.dispatched` 토픽으로 발행

### 4. history-service
- `signal.detected`, `signal.rejected`, `notification.dispatched` 토픽 구독
- MySQL 영구 저장 (`signal_events`, `notification_logs` 테이블)
- REST API: `GET /api/signals`, `GET /api/notifications` (페이지네이션 + 필터)
- 웹 대시보드: `http://localhost:8084/signals`, `http://localhost:8084/notifications`
  - 시그널 목록 탭: 종목 / 타입 / 점수 / DETECTED|REJECTED 상태 / 사유
  - 알림 내역 탭: 채널 / SENT|SUPPRESSED 상태 / 억제 사유
  - 30초 자동 갱신

### 5. trade-planning-service (미구현)
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

### 구현된 테이블 (history-service)

| 테이블 | 설명 |
|--------|------|
| `signal_events` | 감지/거부된 시그널 이력 (status: DETECTED \| REJECTED) |
| `notification_logs` | 알림 발송 이력 (channel: TELEGRAM \| SLACK, status: SENT \| SUPPRESSED) |

```sql
signal_events
  ├─ id, stock_code, stock_name
  ├─ signal_types   -- comma-separated (VOLUME_SURGE, GAP_UP, ...)
  ├─ score          -- 시그널 점수
  ├─ status         -- DETECTED | REJECTED
  ├─ reasons        -- 탐지 사유 (JSON array)
  ├─ rejection_reason -- 거부 사유
  └─ event_at, trace_id, created_at

notification_logs
  ├─ id, stock_code, stock_name
  ├─ signal_types, score
  ├─ channel        -- TELEGRAM | SLACK
  ├─ status         -- SENT | SUPPRESSED
  ├─ suppress_reason -- 억제 사유 (쿨다운 / dedup / rate-limit)
  └─ dispatched_at, trace_id, created_at
```

### 향후 계획 테이블

| 테이블 | 설명 |
|--------|------|
| `validation_results` | AI 검증 결과 |
| `trade_plans` | Shadow Trading 계획 |
| `strategy_config` | 전략 파라미터 설정 |

---
## Kafka Topics

| 구분 | 토픽 | 설명 |
|------|------|------|
| Raw | `raw.market` | 가격 / 거래량 이벤트 |
| Raw | `raw.news` | 뉴스 / 공시 이벤트 |
| Raw | `raw.after-hours` | 시간외 거래대기 물량 / 갭 데이터 |
| Signal | `signal.detected` | 탐지된 유효 신호 |
| Signal | `signal.rejected` | 필터링된 신호 |
| Notification | `notification.dispatched` | 알림 발송 결과 (SENT \| SUPPRESSED) |
| DLQ | `raw.*.dlq` | raw 토픽 처리 실패 메시지 |
| DLQ | `signal.detected.dlq` | 신호 처리 실패 메시지 |
| Future | `trade.plan` | 자동매매 계획 (예정) |

---
## Local Development

### 1. 인프라 실행

```bash
docker compose up -d
```

### 2. MySQL DB 설정

`.env` 파일에 DB 자격증명을 입력합니다:

```bash
DB_ROOT_PASSWORD=secret
DB_NAME=trading
DB_USER=trading
DB_PASSWORD=trading
```

### 3. Kafka 토픽 생성

```bash
./scripts/kafka/create-topics.sh
```

### 4. 대시보드 접속

```
http://localhost:8084/signals       # 시그널 목록
http://localhost:8084/notifications # 알림 내역
```

---

## Author

- Backend Developer
- Event-driven Architecture / Kafka / AI Trading System
