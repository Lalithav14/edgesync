"""
EdgeSync - Insight Generation Lambda

Triggered by SQS (pushed by the ingestion Lambda every N writes). Pulls the
most recent readings for a device from DynamoDB, asks Claude to summarize
whether anything looks anomalous in plain English, and stores the result.

This is the "AI-powered developer productivity tool" piece of the JD, but
applied to the product itself rather than the build process.
"""
import json
import os
import time
import urllib.request

import boto3
from boto3.dynamodb.conditions import Key

dynamodb = boto3.resource("dynamodb")
READINGS_TABLE = os.environ["TABLE_NAME"]
INSIGHTS_TABLE = os.environ["INSIGHTS_TABLE_NAME"]
ANTHROPIC_API_KEY = os.environ["ANTHROPIC_API_KEY"]
LOOKBACK = int(os.environ.get("LOOKBACK_READINGS", "20"))

readings_table = dynamodb.Table(READINGS_TABLE)
insights_table = dynamodb.Table(INSIGHTS_TABLE)


def handler(event, context):
    for record in event["Records"]:
        body = json.loads(record["body"])
        device_id = body["device_id"]
        _process_device(device_id)
    return {"statusCode": 200}


def _process_device(device_id):
    resp = readings_table.query(
        KeyConditionExpression=Key("device_id").eq(device_id),
        ScanIndexForward=False,  # most recent first
        Limit=LOOKBACK,
    )
    items = resp.get("Items", [])
    if not items:
        return

    readings_text = "\n".join(f"temp={i['temp']}C at ts={i['ts']}" for i in reversed(items))

    summary = _call_claude(readings_text)

    insights_table.put_item(Item={
        "device_id": device_id,
        "summary": summary,
        "generated_at": int(time.time()),
    })


def _call_claude(readings_text: str) -> str:
    prompt = (
        "Here are recent sensor readings from an IoT device, oldest to newest:\n\n"
        f"{readings_text}\n\n"
        "In one or two short sentences, plain English, tell the user whether "
        "anything looks unusual (a spike, a steady drift, etc.) or if things "
        "look normal. Be concise and specific with numbers."
    )

    payload = json.dumps({
        "model": "claude-sonnet-4-6",
        "max_tokens": 200,
        "messages": [{"role": "user", "content": prompt}],
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "x-api-key": ANTHROPIC_API_KEY,
            "anthropic-version": "2023-06-01",
        },
    )

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read())
            return data["content"][0]["text"]
    except Exception as e:
        # Never let an AI call failure break the pipeline — degrade gracefully.
        return f"(insight unavailable: {e})"
