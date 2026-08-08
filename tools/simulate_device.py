"""
EdgeSync - Device Simulator

Posts fake sensor readings straight to the deployed API, so you can test
the ingestion -> SQS -> insight -> get_insight pipeline end-to-end before
the ESP32 is wired up or even before the Android app is installed anywhere.

Usage:
    python3 simulate_device.py --url https://xxxx.execute-api.us-east-1.amazonaws.com/Prod/
"""
import argparse
import json
import random
import time
import urllib.request

def post_reading(base_url: str, device_id: str, temp: float, ts: int):
    url = base_url.rstrip("/") + "/readings"
    body = json.dumps({"device_id": device_id, "temp": temp, "ts": ts}).encode()
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return resp.status

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True, help="API base URL from `sam deploy` outputs")
    parser.add_argument("--device-id", default="sim-device-1")
    parser.add_argument("--count", type=int, default=30)
    parser.add_argument("--interval", type=float, default=2.0)
    args = parser.parse_args()

    base = 24.0
    for i in range(args.count):
        base += random.uniform(-1, 1)
        if random.randint(0, 10) == 0:
            base += 8  # inject an anomaly so the AI insight has something to notice
        base = max(15, min(45, base))
        ts = int(time.time() * 1000)

        status = post_reading(args.url, args.device_id, round(base, 1), ts)
        print(f"[{i+1}/{args.count}] posted temp={base:.1f} status={status}")
        time.sleep(args.interval)

if __name__ == "__main__":
    main()
