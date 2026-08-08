"""
EdgeSync - Get Insight Lambda

Simple read endpoint the Android app polls: GET /insights/{deviceId}
Returns the most recently generated AI summary for that device, if any.
"""
import json
import os

import boto3

dynamodb = boto3.resource("dynamodb")
INSIGHTS_TABLE = os.environ["INSIGHTS_TABLE_NAME"]
table = dynamodb.Table(INSIGHTS_TABLE)


def handler(event, context):
    device_id = event["pathParameters"]["deviceId"]
    resp = table.get_item(Key={"device_id": device_id})
    item = resp.get("Item")

    body = {
        "summary": item["summary"] if item else None,
        "generated_at": item.get("generated_at") if item else None,
    }
    return {
        "statusCode": 200,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(body),
    }
