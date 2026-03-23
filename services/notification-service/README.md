# notification-service

`signal.detected` 토픽의 유효 신호를 바탕으로 Telegram 및 Slack으로 알림을 발송하는 서비스입니다.

---

## Overview

`notification-service` 는 AI Trading Signal Platform의 알림 발송 서비스입니다.

`signal-service` 가 탐지하고 검증한 매매 신호(`signal.detected`)를 소비하여 사전 등록된 채널로 실시간 알림을 전송합니다.
Redis를 이용한 중복 방지 / 쿨다운 / 채널별 속도 제한을 통해 과도한 알림 발송을 억제합니다.

내부는 3개 모듈로 구성되어 각 책임을 분리합니다.

| 모듈 | 책임 |
|------|------|
| **dedup** | Redis 기반 중복 방지 / 쿨다운 / 채널별 속도 제한 |
| **formatter** | 신호 이벤트 → 한국어 메시지 변환 |
| **sender** | 채널별 알림 발송 (Telegram, Slack) |

---

## Responsibilities

### 1. Kafka 신호 소비

- `signal.detected` 토픽 구독 (`consumer group: notification-service`)
- `SignalDetectedEvent` 역직렬화 및 traceId MDC 전파
- 처리 실패 시 `signal.detected.dlq` 로 라우팅

### 2. 알림 억제 정책 (Redis)

- **Dedup**: 동일 채널 + 동일 종목 + 동일 신호 유형 조합 중복 방지 (TTL 내 재발송 차단)
- **Cooldown**: 종목별 전체 채널 공통 쿨다운 (최근 알림 발송 후 일정 시간 억제)
- **Rate Limit**: 채널별 단위 시간 내 발송 건수 상한 제어

### 3. 메시지 포맷

- 신호 유형 → 한국어 레이블 변환
- 점수 구간별 이모지 적용 (🔥 80+, ⚡ 60+, 📈 25+)
- Telegram / Slack 각 채널 마크다운 포맷 대응

### 4. 채널 활성화 제어

각 채널은 **독립적으로 활성화/비활성화**할 수 있습니다.

| 채널 | 활성 조건 | 비활성 시 동작 |
|------|----------|--------------|
| Telegram | `notification.telegram.enabled=true` AND `bot-token` 설정 | 해당 채널 스킵, 다른 채널 정상 발송 |
| Slack | `notification.slack.enabled=true` AND `webhook-url` 설정 | 해당 채널 스킵, 다른 채널 정상 발송 |

- 두 채널 모두 비활성이면 알림 없이 로그만 기록합니다.
- `enabled=true` 이더라도 필수 설정값(token/webhook-url)이 비어 있으면 자동으로 비활성 처리됩니다.

### 5. 알림 발송

- **Telegram**: Bot API (`/sendMessage`, `parse_mode=Markdown`)
- **Slack**: Incoming Webhook (`POST webhookUrl`)
- 채널별 독립 발송 — 한 채널 실패가 다른 채널 발송에 영향 없음

---

## Non-Responsibilities

`notification-service` 는 아래 작업을 수행하지 않습니다.

- 매매 신호 탐지 / 점수 계산 (signal-service 역할)
- 이력 저장 / DB 쓰기 (history-service 역할)
- 실제 매매 주문 (trade-planning-service 역할)
- 외부 시장 데이터 수집 (collector-service 역할)

---

## Architecture Role

```text
Kafka
  └─ signal.detected
          │
          ▼
[notification-service]
  ├─ [listener]    @KafkaListener → SignalDetectedEvent 수신
  │
  ├─ [dedup]       Redis 억제 정책 검사
  │     ├─ score < minScore → skip
  │     ├─ cooldown 중 → skip
  │     ├─ dedup TTL 내 → skip
  │     └─ rate limit 초과 → skip
  │
  ├─ [formatter]   SignalDetectedEvent → 한국어 메시지
  │
  └─ [sender]      채널별 독립 발송
        ├─ TelegramNotificationSender
        │     ├─ enabled=false OR bot-token 미설정 → skip (로그)
        │     └─ enabled=true  AND bot-token 설정  → Telegram Bot API 호출
        └─ SlackNotificationSender
              ├─ enabled=false OR webhook-url 미설정 → skip (로그)
              └─ enabled=true  AND webhook-url 설정  → Slack Incoming Webhook 호출
```

---

## Data Flow

```
1. @KafkaListener 수신
   └─ signal.detected (SignalDetectedEvent)
      ├─ stockCode, stockName
      ├─ signalTypes: [VOLUME_SURGE | NEWS_SURGE | AFTER_HOURS_SURGE | GAP_UP]
      ├─ score
      ├─ reasons: []
      ├─ detectedAt
      └─ traceId

2. 점수 게이트
   └─ score < notification.min-score → 스킵

3. 채널별 억제 정책 확인 (Redis)
   ├─ 종목 쿨다운 확인 → 쿨다운 중이면 스킵
   ├─ Dedup 키 확인 → TTL 내 동일 신호면 스킵
   └─ Rate Window 카운터 확인 → 한도 초과면 스킵

4. 메시지 포맷
   └─ SignalMessageFormatter → 채널별 마크다운 문자열

5. 채널별 활성화 확인 + 알림 발송
   ├─ Telegram
   │     ├─ isEnabled() = false → skip
   │     └─ isEnabled() = true  → send() → 성공 시 Redis 기록
   └─ Slack
         ├─ isEnabled() = false → skip
         └─ isEnabled() = true  → send() → 성공 시 Redis 기록

6. 발송 성공 시 Redis 기록 (활성 채널에 한해)
   ├─ 쿨다운 키 등록
   ├─ Dedup 키 등록
   └─ Rate Window 카운터 증가

   ※ 두 채널 모두 비활성인 경우: 알림 없이 WARN 로그만 기록
```

---

## Notification Message Format

```
🔥 **매매신호 감지**
━━━━━━━━━━━━━━━━━━━━
종목: **삼성전자** (005930)
신호: 거래량급증 + 갭상승
점수: **85점**
━━━━━━━━━━━━━━━━━━━━
근거:
  • 거래량 급증: 현재량 2.5배 초과
  • 갭상승: 전일 종가 대비 +4.2%
━━━━━━━━━━━━━━━━━━━━
감지시각: 03/23 09:32 KST
TraceID: `a1b2c3d4`
```

| 점수 구간 | 이모지 | 의미 |
|----------|--------|------|
| 80 이상 | 🔥 | 강력 신호 |
| 60 ~ 79 | ⚡ | 주목 신호 |
| 25 ~ 59 | 📈 | 일반 신호 |

---

## Key Events

### 소비 이벤트 (Input)

| 토픽 | 이벤트 | Key |
|------|--------|-----|
| `signal.detected` | SignalDetectedEvent | stockCode |

### Redis 키 구조

| 키 패턴 | 용도 | TTL |
|---------|------|-----|
| `notif:cooldown:{stockCode}` | 종목별 전체 채널 쿨다운 | `cooldown-minutes` |
| `notif:dedup:{channel}:{stockCode}:{signalFingerprint}` | 채널별 중복 방지 | `dedup.ttl-minutes` |
| `notif:rate:{channel}:{windowKey}` | 채널별 발송 속도 제한 | `rate-limit.window-minutes` |

---

## Configuration

| 키 | 설명 | 기본값 |
|----|------|--------|
| `notification.min-score` | 알림 발송 최소 점수 | `25` |
| `notification.telegram.enabled` | Telegram 발송 활성화 | `true` |
| `notification.telegram.bot-token` | Telegram Bot Token | (필수) |
| `notification.telegram.chat-id` | Telegram Chat ID | (필수) |
| `notification.slack.enabled` | Slack 발송 활성화 | `true` |
| `notification.slack.webhook-url` | Slack Incoming Webhook URL | (필수) |
| `notification.slack.username` | Slack 봇 표시 이름 | `Trading Signal Bot` |
| `notification.slack.icon-emoji` | Slack 봇 아이콘 | `:chart_with_upwards_trend:` |
| `notification.rate-limit.max-per-window` | 채널별 윈도우 내 최대 발송 건수 | `10` |
| `notification.rate-limit.window-minutes` | 속도 제한 롤링 윈도우 크기 | `60` |
| `notification.rate-limit.cooldown-minutes` | 종목별 쿨다운 시간 | `30` |
| `notification.dedup.ttl-minutes` | 동일 신호 중복 방지 TTL | `60` |

### 채널 온오프 예시

```yaml
# Telegram만 활성
notification:
  telegram:
    enabled: true
    bot-token: ${TELEGRAM_BOT_TOKEN}
    chat-id: ${TELEGRAM_CHAT_ID}
  slack:
    enabled: false

# Slack만 활성
notification:
  telegram:
    enabled: false
  slack:
    enabled: true
    webhook-url: ${SLACK_WEBHOOK_URL}

# 둘 다 활성 (기본)
notification:
  telegram:
    enabled: true
    bot-token: ${TELEGRAM_BOT_TOKEN}
    chat-id: ${TELEGRAM_CHAT_ID}
  slack:
    enabled: true
    webhook-url: ${SLACK_WEBHOOK_URL}
```

> `enabled=true`여도 `bot-token` / `webhook-url`이 비어 있으면 자동으로 비활성 처리됩니다.

### 환경 변수

| 변수 | 설명 |
|------|------|
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API 토큰 |
| `TELEGRAM_CHAT_ID` | 알림 수신 Chat ID |
| `SLACK_WEBHOOK_URL` | Slack Incoming Webhook URL |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소 (기본: `localhost:29092`) |
| `REDIS_HOST` | Redis 호스트 (기본: `localhost`) |
| `REDIS_PORT` | Redis 포트 (기본: `6379`) |

---

## Tech Stack

| 분류            | 기술                                         |
|---------------|--------------------------------------------|
| Runtime       | Java 21, Spring Boot 3.5                   |
| Messaging     | Spring Kafka (Consumer)                    |
| HTTP Client   | Spring WebFlux WebClient (비동기 HTTP)        |
| State / Cache | Spring Data Redis                          |
| Logging       | Logstash Logback Encoder (JSON)            |
| Test          | JUnit 5, Spring Kafka Test (EmbeddedKafka) |
