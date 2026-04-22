# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## KIS OpenAPI 사용 규칙

**KIS 공식 문서에 존재하는 API만 사용해야 한다.**

- 모든 KIS API 엔드포인트와 `tr_id`는 반드시 [KIS Developers 공식 포털](https://apiportal.koreainvestment.com) 또는 [공식 GitHub](https://github.com/koreainvestment/open-trading-api)에서 확인된 것만 사용한다.
- 패턴 유추로 API를 추측하거나 만들어내지 않는다 (예: 존재하는 `FHPST02300000`을 보고 `FHPST02310000`을 추론하는 행위 금지).
- API 스펙이 불확실한 경우 반드시 사용자에게 공식 문서 확인을 요청한다.

**현재 확인된 실제 KIS 시간외 관련 API:**

| tr_id | 설명 |
|---|---|
| `FHPST02300000` | 국내주식 시간외단일가 현재가 (`/uapi/domestic-stock/v1/quotations/inquire-overtime-price`) |
| `FHPST02320000` | 국내주식 시간외 일자별 주가 |
| `FHPST01760000` | 장전/장후 시간외 잔량 순위 (`/uapi/domestic-stock/v1/ranking/after-hour-balance`) |

> 시간외 호가 잔량을 종목별로 조회하는 REST API는 KIS 공식 문서에 존재하지 않는다.

## Build & Test Commands

```bash
# Build all services
./gradlew build

# Run all tests
./gradlew test

# Build/test a specific service
./gradlew :services:collector-service:build
./gradlew :services:signal-service:build
./gradlew :services:notification-service:build
./gradlew :services:history-service:build

# Run a single test class
./gradlew :services:signal-service:test --tests "com.example.signal.SomeTest"

# Start local dev infrastructure (Kafka, Redis, Kafka UI at :8989)
docker compose up -d

# Create Kafka topics
./scripts/kafka/create-topics.sh

# Start production stack
docker compose -f docker-compose-prod.yml up -d
```

## Architecture Overview

This is an **event-driven microservices platform** for Korean stock market signal detection. Four services are implemented; one more (trade-planning-service) is planned.

### Data Flow

```
External APIs (KIS, DART)
    ↓
collector-service (:8081)   — ingests price/volume/news/after-hours data, publishes to Kafka
    ↓
Kafka raw.* topics (raw.market, raw.news, raw.after-hours)
    ↓
signal-service (:8082)      — Scanner → Scorer → Validator pipeline
    ↓
Kafka signal.* topics (signal.detected, signal.rejected)
    ↓
notification-service (:8083) — sends Telegram/Slack alerts
    ↓                          publishes notification.dispatched (SENT | SUPPRESSED)
Kafka notification.dispatched
    ↓
history-service (:8084)     — persists signal_events + notification_logs to MySQL
                               serves web dashboard at /signals and /notifications
```

### Services

| Service | Port | Role |
|---|---|---|
| **collector-service** | 8081 | Polls KIS (price/volume/after-hours) and DART (disclosures) APIs on 30s intervals; publishes raw events to Kafka |
| **signal-service** | 8082 | Core analysis engine with three stages: Scanner (detects conditions), Scorer (weighted scoring), Validator (Redis-backed dedup/cooldown/burst checks) |
| **notification-service** | 8083 | Dispatches Telegram/Slack alerts; Redis-backed rate limiting (10/hr) and dedup (60min cooldown); publishes `notification.dispatched` event after each dispatch attempt |
| **history-service** | 8084 | Consumes `signal.detected`, `signal.rejected`, `notification.dispatched`; persists to MySQL; serves Thymeleaf dashboard + REST API |

### Signal Detection (signal-service)

4 signal types are detected and scored:
- **VolumeSurge**: volume ratio > 2.5× vs previous day (weight: 25)
- **NewsSurge**: 2+ DART disclosures in 60-min window with bullish keywords (weight: 25)
- **AfterHoursSurge**: buy/sell ratio > 2.0 in evening orders (weight: 30)
- **GapUp**: opens 2%+ above previous close within 45 min of market open (weight: 25)
- **Breakout**: 20-day high penetration bonus (weight: 35)

Combo bonuses apply (+10 for 2 signals, +20 for 3, +30 for 4). Minimum score to emit `signal.detected` is 40.

### Key Technology

- **Framework**: Spring Boot 3.5.12, Java 21
- **Build**: Gradle with Kotlin DSL (`build.gradle.kts` per service)
- **Messaging**: Apache Kafka 4.1.0 — JSON serialization, `acks=all`, idempotent producer, 3 partitions per topic, dead-letter queues (`*.dlq`)
- **Cache/State**: Redis 7 — dedup TTLs (60–480 min by signal type), burst counting, price/volume snapshots
- **HTTP Clients**: OpenFeign with Resilience4j circuit breaker and Spring Retry AOP (KIS: 19 req/s, DART: 1 req/s limits)
- **Reactive**: notification-service uses Spring WebFlux

### Key Kafka Topics

| Topic | Producer | Consumer |
|---|---|---|
| `raw.market` / `raw.news` / `raw.after-hours` | collector-service | signal-service |
| `signal.detected` / `signal.rejected` | signal-service | notification-service, history-service |
| `notification.dispatched` | notification-service | history-service |

### Configuration Highlights

Signal thresholds, scorer weights, bullish/bearish keywords, and validator TTLs are all externalized in `application.yml` for each service — no magic numbers in code. Key files:
- `services/signal-service/src/main/resources/application.yml` — all scoring/detection thresholds
- `services/notification-service/src/main/resources/application.yml` — channel toggles, rate limits
- `services/history-service/src/main/resources/application.yml` — DB connection, Kafka topics
- `.env` — API credentials (KIS, DART), Telegram/Slack tokens, DB credentials, stock watchlist

### history-service Dashboard

Web UI served by history-service (Thymeleaf + Bootstrap):
- `http://localhost:8084/signals` — signal list with status filter (DETECTED/REJECTED), stock code search, pagination
- `http://localhost:8084/notifications` — notification log with channel/status filter, suppression reason display
- Both pages auto-refresh every 30 seconds
- REST API: `GET /api/signals`, `GET /api/notifications` (pageable, filterable)

### Planned Services (not yet implemented)

- **trade-planning-service**: Shadow trading simulation (no real orders)

MySQL is active in docker-compose (history-service dependency). Set DB credentials in `.env` before running `docker compose up -d`.
