# EdgeSync

A BLE-connected IoT monitoring app: an ESP32 sensor streams readings to an
Android app, which pushes them to a serverless AWS backend. Readings are
stored in DynamoDB, and an async pipeline uses Claude to generate a
plain-English anomaly summary that's surfaced back in the app.

## Architecture

```
ESP32 (BLE) --> Android App --> API Gateway --> Ingest Lambda --> DynamoDB (readings)
                                                        |
                                                        v (every N writes)
                                                     SQS Queue
                                                        |
                                                        v
                                          Insight Lambda --> Claude API
                                                        |
                                                        v
                                              DynamoDB (insights)
                                                        ^
                                                        |
                                Android App <-- API Gateway <-- Get-Insight Lambda
```

**Why this shape??:**
- **DynamoDB over RDS** — sensor writes are high-frequency, simple key-value
  access (`device_id` + `ts`), no joins needed. Pay-per-request billing keeps
  it free-tier friendly for a side project.
- **SQS between ingestion and AI generation** — decouples the two. If the AI
  call is slow, rate-limited, or the Lambda is being redeployed, ingestion
  keeps accepting readings without interruption. This is the "fault-tolerant,
  resilient distributed system" story.
- **Infra as code (SAM template)** — the whole backend (2 tables, 1 queue, 3
  functions, 1 alarm) is defined in `backend/template.yaml` and deployed with
  one command, not clicked together in the console.

## Repo layout

```
firmware/          ESP32 Arduino sketch (BLE sensor broadcast)
android/            Android app (Kotlin, Jetpack Compose, MVVM)
backend/            AWS SAM project (Lambdas + template.yaml)
tools/              simulate_device.py - test the pipeline without hardware
.github/workflows/  CI for both Android and backend
```


### 1. Deploy the backend
```bash
cd backend
sam build
sam deploy --guided
# Prompts for AnthropicApiKey - use an API key from console.anthropic.com
```
Note the `ApiUrl` output — you'll need it in step 2 and 3.

### 2. Test the pipeline without hardware
```bash
cd tools
python3 simulate_device.py --url <ApiUrl from step 1>
```
Wait ~15s after it posts a few readings, then check:
```bash
curl <ApiUrl>/insights/sim-device-1
```
You should see an AI-generated summary once 5 readings have been posted.

### 3. Run the Android app (simulated mode)
1. Open `android/` in Android Studio.
2. In `network/ApiService.kt`, replace `BASE_URL` with your `ApiUrl` from step 1.
3. Run on an emulator or device. `MainActivity` starts in simulated mode by
   default (`useSimulated = true`), so you'll see live fake readings and,
   after ~15-30s, an AI insight — no BLE or ESP32 required yet.

### 4. Hardware
1. Flash `firmware/esp32_ble_sensor/esp32_ble_sensor.ino` to your ESP32
   (Arduino IDE, board = your ESP32 dev board). It starts in
   `USE_FAKE_SENSOR` mode so you can verify BLE broadcast before wiring a
   real sensor.
2. Wire your real sensor and replace `readSensor()` in the firmware.
3. In `MainActivity.kt`, change `useSimulated = false` and grant BLE
   permissions on the device at runtime (not yet wired up in this skeleton —
   add a permission request flow, a good small feature to add yourself).

## CI/CD

- `.github/workflows/android-ci.yml` — builds and unit-tests the app on
  every push. **Note:** you'll need to run `gradle wrapper` inside
  `android/` once in Android Studio to generate the `gradlew` wrapper files
  before pushing (not included here since they're binary/generated files).
- `.github/workflows/backend-ci.yml` — lints and validates the SAM template
  on every push.



