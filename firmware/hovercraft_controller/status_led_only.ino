#include <Arduino.h>
#include <NimBLEDevice.h>

static const char *DEVICE_NAME = "Wobble Wagon";
static const char *SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB";
static const char *COMMAND_CHAR_UUID = "0000BEEF-0000-1000-8000-00805F9B34FB";

static const uint8_t LED_BRIGHTNESS = 80;

static bool isConnected = false;
static bool isBlinking = false;
static uint32_t blinkUntilMs = 0;
static uint32_t lastBlinkMs = 0;
static bool blinkOn = false;
static NimBLEServer *bleServer = nullptr;

static void setLed(uint8_t r, uint8_t g, uint8_t b) {
#ifdef RGB_BUILTIN
  rgbLedWrite(RGB_BUILTIN, r, g, b);
#else
  (void)r;
  (void)g;
  (void)b;
#endif
}

static void logState(const char *message) {
  Serial.println(message);
}

class ServerCallbacks : public NimBLEServerCallbacks {
 public:
  void onConnect(NimBLEServer *server) {
    (void)server;
    handleConnect();
  }

  void onDisconnect(NimBLEServer *server) {
    (void)server;
    handleDisconnect();
  }

  void onConnect(NimBLEServer *server, ble_gap_conn_desc *desc) {
    (void)server;
    (void)desc;
    handleConnect();
  }

  void onDisconnect(NimBLEServer *server, ble_gap_conn_desc *desc) {
    (void)server;
    (void)desc;
    handleDisconnect();
  }

 private:
  void handleConnect() {
    isConnected = true;
    isBlinking = true;
    blinkOn = false;
    blinkUntilMs = millis() + 1500;
    logState("BLE: connected");
  }

  void handleDisconnect() {
    isConnected = false;
    isBlinking = false;
    blinkOn = false;
    logState("BLE: disconnected");
    NimBLEDevice::getAdvertising()->start();
  }
};

void setup() {
  Serial.begin(115200);
  logState("Boot: status_led_only");

#ifdef RGB_BUILTIN
  pinMode(RGB_BUILTIN, OUTPUT);
#endif

  setLed(LED_BRIGHTNESS, LED_BRIGHTNESS, LED_BRIGHTNESS);
  delay(800);
  logState("LED: white (boot)");

  NimBLEDevice::init(DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);
  logState("BLE: init");
  Serial.print("BLE: address ");
  Serial.println(NimBLEDevice::getAddress().toString().c_str());

  NimBLEServer *server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  bleServer = server;

  NimBLEService *service = server->createService(SERVICE_UUID);
  service->createCharacteristic(
      COMMAND_CHAR_UUID,
      NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  service->start();
  logState("BLE: service started");

  NimBLEAdvertising *advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  NimBLEAdvertisementData advData;
  advData.setName(DEVICE_NAME);
  advData.setCompleteServices(NimBLEUUID(SERVICE_UUID));
  advertising->setAdvertisementData(advData);

  NimBLEAdvertisementData scanData;
  scanData.setName(DEVICE_NAME);
  advertising->setScanResponseData(scanData);
  advertising->start();
  Serial.print("BLE: advertising as ");
  Serial.println(DEVICE_NAME);
}

void loop() {
  const uint32_t now = millis();
  if (bleServer != nullptr) {
    const bool connectedNow = bleServer->getConnectedCount() > 0;
    if (connectedNow && !isConnected) {
      isConnected = true;
      isBlinking = true;
      blinkOn = false;
      blinkUntilMs = millis() + 1500;
      logState("BLE: connected (poll)");
    } else if (!connectedNow && isConnected) {
      isConnected = false;
      isBlinking = false;
      blinkOn = false;
      logState("BLE: disconnected (poll)");
      NimBLEDevice::getAdvertising()->start();
    }
  }

  if (isBlinking) {
    if (now - lastBlinkMs >= 200) {
      lastBlinkMs = now;
      blinkOn = !blinkOn;
      if (blinkOn) {
        setLed(LED_BRIGHTNESS, LED_BRIGHTNESS, LED_BRIGHTNESS);
      } else {
        setLed(0, 0, 0);
      }
    }
    if (now >= blinkUntilMs) {
      isBlinking = false;
      blinkOn = false;
      logState("LED: blink done");
    }
  } else if (isConnected) {
    setLed(0, LED_BRIGHTNESS, 0);
  } else {
    setLed(0, 0, LED_BRIGHTNESS);
  }

  delay(20);
}
