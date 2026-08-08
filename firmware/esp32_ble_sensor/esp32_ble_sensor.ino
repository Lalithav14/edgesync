/*
 * EdgeSync - ESP32 BLE Sensor Firmware
 *
 * Reads a sensor (defaults to a fake/simulated reading if no sensor wired yet)
 * and broadcasts it as a JSON string over a BLE GATT characteristic.
 *
 * Board: any ESP32 dev board
 * Library required: "ESP32 BLE Arduino" (built into the ESP32 board package)
 *
 * Once you wire a real sensor (e.g. DHT22, or anything on an analog pin),
 * replace readSensor() with the real reading. Until then, USE_FAKE_SENSOR
 * lets you flash + test the BLE broadcast today without any wiring.
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ---- Config ----
#define DEVICE_NAME        "EdgeSync-ESP32"
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define BROADCAST_INTERVAL_MS 3000

// Flip to false once a real sensor is wired up
#define USE_FAKE_SENSOR true

BLECharacteristic *pCharacteristic;
bool deviceConnected = false;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("Client connected");
  }
  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("Client disconnected, restarting advertising");
    pServer->getAdvertising()->start();
  }
};

float readSensor() {
#if USE_FAKE_SENSOR
  // Simulated temperature that drifts slightly + occasional spike,
  // so the app/backend has something realistic to react to.
  static float base = 24.0;
  base += ((float)random(-10, 11)) / 10.0;
  if (random(0, 50) == 0) base += 8.0; // occasional anomaly spike
  if (base < 15) base = 15;
  if (base > 45) base = 45;
  return base;
#else
  // TODO: replace with real sensor read, e.g.:
  // return dht.readTemperature();
  return 0.0;
#endif
}

void setup() {
  Serial.begin(115200);
  randomSeed(analogRead(0));

  BLEDevice::init(DEVICE_NAME);
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharacteristic->addDescriptor(new BLE2902());
  pCharacteristic->setValue("{}");

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("EdgeSync BLE sensor advertising, waiting for client...");
}

void loop() {
  float reading = readSensor();
  unsigned long ts = millis();

  // Keep payload dead simple JSON so the Android app can parse it directly
  char payload[64];
  snprintf(payload, sizeof(payload), "{\"temp\":%.1f,\"ts\":%lu}", reading, ts);

  pCharacteristic->setValue(payload);
  if (deviceConnected) {
    pCharacteristic->notify();
  }

  Serial.println(payload);
  delay(BROADCAST_INTERVAL_MS);
}
