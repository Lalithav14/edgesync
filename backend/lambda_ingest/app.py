"""
EdgeSync - Ingestion Lambda

Receives a sensor reading from the Android app (via API Gateway), writes it
to DynamoDB, and every WRITE_BATCH_SIZE-th write, pushes a message to SQS
to trigger the async AI insight Lambda.

This decoupling is the point: if the insight service is slow, erroring, or
being redeployed, ingestion keeps working uninterrupted. That's the
fault-tolerance story for the resume/interview.
"""
import json
import os
import time
import uuid

import boto3

dynamodb = boto3.resource("dynamodb")
sqs = boto3.client("sqs")

TABLE_NAME = os.environ["TABLE_NAME"]
QUEUE_URL = os.environ["INSIGHT_QUEUE_URL"]
WRITE_BATCH_SIZE = int(os.environ.get("WRITE_BATCH_SIZE", "5"))

table = dynamodb.Table(TABLE_NAME)

# Simple in-memory counter per warm Lambda instance. Good enough for a
# student project; for real production you'd track this in DynamoDB itself
# or use a Kinesis stream instead. Worth mentioning as a known limitation.
_write_counts = {}


def handler(event, context):
    try:
        body = json.loads(event.get("body") or "{}")
        device_id = body["device_id"]
        temp = float(body["temp"])
        ts = int(body["ts"])
    except (KeyError, ValueError, TypeError) as e:
        return _response(400, {"error": f"invalid payload: {e}"})

    item = {
        "device_id": device_id,
        "ts": ts,
        "temp": str(temp),  # DynamoDB Decimal quirks avoided by storing as string
        "reading_id": str(uuid.uuid4()),
        "received_at": int(time.time()),
    }
    table.put_item(Item=item)

    _write_counts[device_id] = _write_counts.get(device_id, 0) + 1
    if _write_counts[device_id] >= WRITE_BATCH_SIZE:
        _write_counts[device_id] = 0
        sqs.send_message(
            QueueUrl=QUEUE_URL,
            MessageBody=json.dumps({"device_id": device_id}),
        )

    return _response(200, {"status": "ok"})


def _response(status_code, body_dict):
    return {
        "statusCode": status_code,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(body_dict),
    }
