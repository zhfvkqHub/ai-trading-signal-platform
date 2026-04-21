# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

This is an **event-driven microservices platform** for Korean stock market signal detection. Three services are implemented; two more (history-service, trade-planning-service) are planned.

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
```

### Services

| Service | Port | Role |
|---|---|---|
| **collector-service** | 8081 | Polls KIS (price/volume/after-hours) and DART (disclosures) APIs on 30s intervals; publishes raw events to Kafka |
| **signal-service** | 8082 | Core analysis engine with three stages: Scanner (detects conditions), Scorer (weighted scoring), Validator (Redis-backed dedup/cooldown/burst checks) |
| **notification-service** | 8083 | Dispatches Telegram/Slack alerts; Redis-backed rate limiting (10/hr) and dedup (60min cooldown) |

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

### Configuration Highlights

Signal thresholds, scorer weights, bullish/bearish keywords, and validator TTLs are all externalized in `application.yml` for each service — no magic numbers in code. Key files:
- `services/signal-service/src/main/resources/application.yml` — all scoring/detection thresholds
- `services/notification-service/src/main/resources/application.yml` — channel toggles, rate limits
- `.env` / `.env.example` — API credentials (KIS, DART), Telegram/Slack tokens, stock watchlist

### Planned Services (not yet implemented)

- **history-service**: MySQL persistence for signals, alerts, validation results
- **trade-planning-service**: Shadow trading simulation (no real orders)

MySQL is wired in docker-compose but commented out pending history-service implementation.
