import os
import time
import requests
from datetime import datetime
from dotenv import load_dotenv

# .env 파일 로드 (프로젝트 루트)
load_dotenv(os.path.join(os.path.dirname(__file__), "..", "..", ".env"))

# ===== 설정 =====
PRODUCT_URL = "https://nolmdshop.com/product/2428/category/30/display/3/"
SLACK_WEBHOOK_URL = os.getenv("STOCK_MONITOR_SLACK_WEBHOOK_URL", "")
CHECK_INTERVAL = int(os.getenv("STOCK_MONITOR_INTERVAL", "180"))

# Cafe24 Front API
CAFE24_API_BASE = "https://ticketmdshop.cafe24api.com/api/v2"
CAFE24_CLIENT_ID = "A8RQp67UIt9nBlqvThz2jC"
UNIFORM_PRODUCT_NO = 2428
MARKING_KIT_PRODUCT_NO = 2430

# 관심 사이즈만 알림 받으려면 지정. 전체 알림이면 빈 리스트 []
TARGET_SIZES = []  # 예: ["95", "100"]

# 마킹키트(추가구성상품)도 모니터링할지
MONITOR_MARKING_KIT = True
TARGET_PLAYERS = []  # 예: ["양석환(53)", "김택연(63)"]. 전체면 빈 리스트

last_uniform_stock = {}
last_marking_stock = {}


def send_slack(title, fields, color="#36a64f"):
    payload = {
        "attachments": [{
            "color": color,
            "title": title,
            "title_link": PRODUCT_URL,
            "fields": fields,
            "footer": "두산 쿵야 콜라보 재고 모니터",
            "ts": int(time.time()),
        }]
    }
    try:
        resp = requests.post(SLACK_WEBHOOK_URL, json=payload, timeout=10)
        if resp.status_code != 200:
            print(f"[슬랙 전송 실패] {resp.status_code} {resp.text}")
    except Exception as e:
        print(f"[슬랙 전송 에러] {e}")


def fetch_variants(product_no):
    url = f"{CAFE24_API_BASE}/products/{product_no}/variants"
    headers = {
        "Content-Type": "application/json",
        "X-Cafe24-Client-Id": CAFE24_CLIENT_ID,
    }
    resp = requests.get(url, headers=headers, timeout=15)
    resp.raise_for_status()
    return resp.json().get("variants", [])


def check_stock():
    global last_uniform_stock, last_marking_stock

    try:
        variants = fetch_variants(UNIFORM_PRODUCT_NO)
    except Exception as e:
        print(f"[유니폼 API 요청 실패] {e}")
        return

    # ----- 1. 유니폼 본품 재고 -----
    current_uniform = {}
    restocked_uniform = []
    for v in variants:
        size = v["options"][0]["value"]
        qty = v["quantity"]
        current_uniform[size] = qty

        if TARGET_SIZES and size not in TARGET_SIZES:
            continue
        if qty > 0 and last_uniform_stock.get(size, 0) == 0:
            restocked_uniform.append((size, qty))

    if restocked_uniform:
        fields = [
            {"title": f"사이즈 {size}", "value": f"{qty}개", "short": True}
            for size, qty in restocked_uniform
        ]
        send_slack("유니폼 재입고!", fields, color="#36a64f")

    last_uniform_stock = current_uniform

    # ----- 2. 마킹키트 재고 -----
    if MONITOR_MARKING_KIT:
        try:
            marking_variants = fetch_variants(MARKING_KIT_PRODUCT_NO)
        except Exception as e:
            print(f"[마킹키트 API 요청 실패] {e}")
            marking_variants = []

        current_marking = {}
        restocked_marking = []
        for v in marking_variants:
            player = v["options"][0]["value"]
            qty = v["quantity"]
            current_marking[player] = qty

            if TARGET_PLAYERS and player not in TARGET_PLAYERS:
                continue
            if qty > 0 and last_marking_stock.get(player, 0) == 0:
                restocked_marking.append((player, qty))

        if restocked_marking:
            fields = [
                {"title": player, "value": f"{qty}개", "short": True}
                for player, qty in restocked_marking
            ]
            send_slack("마킹키트 재입고!", fields, color="#2eb886")

        last_marking_stock = current_marking

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    available = [s for s, q in current_uniform.items() if q > 0]
    print(f"[{now}] 유니폼 재고 있는 사이즈: {available or '없음'}")


def main():
    if not SLACK_WEBHOOK_URL:
        print("[에러] STOCK_MONITOR_SLACK_WEBHOOK_URL 환경변수가 설정되지 않았습니다.")
        print("  .env 파일에 추가하세요:")
        print("  STOCK_MONITOR_SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...")
        return

    print(f"모니터링 시작: {PRODUCT_URL}")
    print(f"체크 주기: {CHECK_INTERVAL}초")

    send_slack(
        "재고 모니터링 시작",
        [
            {"title": "대상", "value": "두산 쿵야 콜라보 유니폼/마킹키트", "short": False},
            {"title": "체크 주기", "value": f"{CHECK_INTERVAL}초", "short": True},
        ],
        color="#439FE0",
    )

    while True:
        check_stock()
        time.sleep(CHECK_INTERVAL)


if __name__ == "__main__":
    main()
