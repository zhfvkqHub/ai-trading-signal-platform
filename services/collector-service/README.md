# collector-service

시장 데이터, 뉴스/공시, 시간외 데이터 등을 수집하여 최소한의 정규화를 거친 뒤 Kafka `raw.*` 토픽으로 발행하는 수집 서비스입니다.

---

## Overview

`collector-service` 는 AI Trading Signal Platform의 데이터 수집 진입점입니다.  
이 서비스는 **좋은 종목을 판단하지 않고**, 외부 데이터 소스로부터 최대한 원본에 가까운 데이터를 가져와 표준 포맷으로 변환한 뒤 Kafka에 전달하는 역할을 담당합니다.

즉, 이 서비스의 책임은 다음과 같습니다.

- 외부 데이터 소스 호출
- 데이터 수집 및 최소 정규화
- 영업일 / 장중 / 장외 시간 판단
- Kafka `raw.*` 이벤트 발행
- 외부 API Rate Limit 및 장애 대응

판단 로직은 `signal-service` 에서 수행하며, `collector-service` 는 데이터 수집과 전달에만 집중합니다.

---

## Responsibilities

### 1. 시장 데이터 수집

- 가격
- 거래량
- 전일 종가
- 시가 / 고가 / 저가
- 시세 수집 시각

### 2. 뉴스 / 공시 수집

- 종목 관련 뉴스
- 공시 / 속보
- 발행 시각
- 출처 정보
- 종목 매핑용 기초 데이터

### 3. 시간외 데이터 수집

- 시간외 거래대기 물량
- 갭상승 판단용 기준 데이터
- 장 종료 후 상태 데이터

### 4. 최소 정규화

- timestamp 포맷 통일
- 숫자 포맷 정리
- null / blank 값 보정
- source 정보 부여
- traceId 연계

### 5. Kafka 발행

- `raw.market`
- `raw.news`
- `raw.after-hours`

### 6. 운영성 고려

- 외부 API Rate Limit 대응
- 재시도 / 백오프
- 장애 시 fallback 고려
- Health Check 제공
- 구조화 로그(JSON) 출력

---

## Non-Responsibilities

`collector-service` 는 아래 작업을 수행하지 않습니다.

- 거래량 급등 판단
- 뉴스 급증 판단
- 갭상승 최종 판단
- 점수 계산
- 중복 신호 제거
- 알림 발송
- 자동매매 판단

위 기능은 `signal-service`, `notification-service`, `trade-planning-service` 의 책임입니다.

---

## Architecture Role

```text
[External Data Sources]
  ├─ Market Data
  ├─ News / Disclosure
  └─ After-hours Data
          │
          ▼
[collector-service]
  ├─ 수집
  ├─ 최소 정규화
  ├─ 영업일 / 장중·장외 판단
  └─ Kafka raw 이벤트 발행
          │
          ▼
Kafka
  ├─ raw.market
  ├─ raw.news
  └─ raw.after-hours
