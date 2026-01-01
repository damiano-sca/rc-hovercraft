#include <Arduino.h>
#include <NimBLEDevice.h>

static const char *DEVICE_NAME = "Wobble Wagon";
static const char *SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB";
static const char *COMMAND_CHAR_UUID = "0000BEEF-0000-1000-8000-00805F9B34FB";

static const uint8_t START_BYTE = 0xA5;
static const uint8_t END_BYTE = 0x5A;
static const uint8_t FLAG_ARM = 0x01;
static const uint8_t FLAG_STOP = 0x02;

static const uint32_t FAILSAFE_MS = 250;

static const uint8_t LED_BRIGHTNESS = 80;

static const int THROTTLE_PWM_PIN = 7;
static const int THROTTLE_PWM_CHANNEL = 0;
static const uint32_t THROTTLE_PWM_FREQ = 2000;
static const uint8_t THROTTLE_PWM_RES = 8;

static const int RUDDER_PWM_PIN = 8;
static const int RUDDER_PWM_CHANNEL = 1;
static const uint32_t RUDDER_PWM_FREQ = 50;
static const uint8_t RUDDER_PWM_RES = 16;
static const int RUDDER_MIN_US = 1000;
static const int RUDDER_MAX_US = 2000;

static bool useRudderPwm = false;
static bool isConnected = false;
static bool isBlinking = false;
static uint32_t blinkUntilMs = 0;
static uint32_t lastBlinkMs = 0;
static bool blinkOn = false;
static NimBLEServer *bleServer = nullptr;
static int throttlePwmChannel = THROTTLE_PWM_CHANNEL;
static int rudderPwmChannel = RUDDER_PWM_CHANNEL;

struct CommandState {
  uint8_t seq = 0;
  uint8_t throttle = 0;
  int8_t rudder = 0;
  bool armed = false;
  bool stop = false;
};

static CommandState lastCommand;
static uint32_t lastPacketMs = 0;

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

static void onBleConnected(const char *source) {
  if (isConnected) {
    return;
  }
  isConnected = true;
  isBlinking = true;
  blinkOn = false;
  blinkUntilMs = millis() + 1500;
  Serial.print("BLE: connected");
  if (source != nullptr) {
    Serial.print(" (");
    Serial.print(source);
    Serial.print(")");
  }
  Serial.println();
}

static void onBleDisconnected(const char *source) {
  if (!isConnected) {
    return;
  }
  isConnected = false;
  isBlinking = false;
  blinkOn = false;
  Serial.print("BLE: disconnected");
  if (source != nullptr) {
    Serial.print(" (");
    Serial.print(source);
    Serial.print(")");
  }
  Serial.println();
  NimBLEDevice::getAdvertising()->start();
}

static uint8_t crc8(const uint8_t *data, size_t length) {
  uint8_t crc = 0;
  for (size_t i = 0; i < length; i++) {
    crc ^= data[i];
    for (int bit = 0; bit < 8; bit++) {
      if (crc & 0x80) {
        crc = (crc << 1) ^ 0x07;
      } else {
        crc <<= 1;
      }
    }
  }
  return crc;
}

static uint32_t usToDuty(uint32_t pulseUs, uint32_t freqHz, uint8_t resolutionBits) {
  uint32_t maxDuty = (1U << resolutionBits) - 1U;
  uint32_t periodUs = 1000000UL / freqHz;
  uint32_t duty = (pulseUs * maxDuty) / periodUs;
  return duty;
}

static void applyOutputs(const CommandState &cmd) {
  uint8_t throttle = (cmd.armed && !cmd.stop) ? cmd.throttle : 0;
  int8_t rudder = (cmd.armed && !cmd.stop) ? cmd.rudder : 0;

  uint32_t throttleDuty = map(throttle, 0, 100, 0, 255);
  ledcWrite(throttlePwmChannel, throttleDuty);

  if (useRudderPwm) {
    int pulseUs = map(rudder, -100, 100, RUDDER_MIN_US, RUDDER_MAX_US);
    uint32_t duty = usToDuty(pulseUs, RUDDER_PWM_FREQ, RUDDER_PWM_RES);
    ledcWrite(rudderPwmChannel, duty);
  }
}

class ServerCallbacks : public NimBLEServerCallbacks {
 public:
  void onConnect(NimBLEServer *server) {
    (void)server;
    onBleConnected("callback");
  }

  void onDisconnect(NimBLEServer *server) {
    (void)server;
    onBleDisconnected("callback");
  }

  void onConnect(NimBLEServer *server, ble_gap_conn_desc *desc) {
    (void)server;
    (void)desc;
    onBleConnected("callback");
  }

  void onDisconnect(NimBLEServer *server, ble_gap_conn_desc *desc) {
    (void)server;
    (void)desc;
    onBleDisconnected("callback");
  }
};

class CommandCallbacks : public NimBLECharacteristicCallbacks {
 public:
  void onWrite(NimBLECharacteristic *characteristic) {
    handleWrite(characteristic);
  }

  void onWrite(NimBLECharacteristic *characteristic, NimBLEConnInfo &connInfo) {
    (void)connInfo;
    handleWrite(characteristic);
  }

 private:
  void handleWrite(NimBLECharacteristic *characteristic) {
    std::string value = characteristic->getValue();
    if (value.size() != 8) {
      return;
    }
    const uint8_t *data = reinterpret_cast<const uint8_t *>(value.data());
    if (data[0] != START_BYTE || data[7] != END_BYTE) {
      return;
    }
    uint8_t expectedCrc = crc8(data, 6);
    if (data[6] != expectedCrc) {
      return;
    }

    CommandState cmd;
    cmd.seq = data[1];
    cmd.throttle = data[2];
    cmd.rudder = static_cast<int8_t>(data[3]);
    cmd.armed = (data[4] & FLAG_ARM) != 0;
    cmd.stop = (data[4] & FLAG_STOP) != 0;

    Serial.print("CMD: seq=");
    Serial.print(cmd.seq);
    Serial.print(" throttle=");
    Serial.print(cmd.throttle);
    Serial.print(" rudder=");
    Serial.print(cmd.rudder);
    Serial.print(" arm=");
    Serial.print(cmd.armed ? "1" : "0");
    Serial.print(" stop=");
    Serial.println(cmd.stop ? "1" : "0");

    lastCommand = cmd;
    lastPacketMs = millis();
    applyOutputs(lastCommand);
  }
};

void setup() {
  Serial.begin(115200);
  logState("Boot: hovercraft_controller");

#ifdef RGB_BUILTIN
  pinMode(RGB_BUILTIN, OUTPUT);
#endif

  setLed(LED_BRIGHTNESS, LED_BRIGHTNESS, LED_BRIGHTNESS);
  delay(800);
  logState("LED: white (boot)");

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  throttlePwmChannel = ledcAttach(THROTTLE_PWM_PIN, THROTTLE_PWM_FREQ, THROTTLE_PWM_RES);
  rudderPwmChannel = ledcAttach(RUDDER_PWM_PIN, RUDDER_PWM_FREQ, RUDDER_PWM_RES);
#else
  ledcSetup(THROTTLE_PWM_CHANNEL, THROTTLE_PWM_FREQ, THROTTLE_PWM_RES);
  ledcAttachPin(THROTTLE_PWM_PIN, THROTTLE_PWM_CHANNEL);
  throttlePwmChannel = THROTTLE_PWM_CHANNEL;

  ledcSetup(RUDDER_PWM_CHANNEL, RUDDER_PWM_FREQ, RUDDER_PWM_RES);
  ledcAttachPin(RUDDER_PWM_PIN, RUDDER_PWM_CHANNEL);
  rudderPwmChannel = RUDDER_PWM_CHANNEL;
#endif

  NimBLEDevice::init(DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);
  logState("BLE: init");
  Serial.print("BLE: address ");
  Serial.println(NimBLEDevice::getAddress().toString().c_str());

  NimBLEServer *server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  bleServer = server;
  NimBLEService *service = server->createService(SERVICE_UUID);
  NimBLECharacteristic *commandCharacteristic = service->createCharacteristic(
      COMMAND_CHAR_UUID,
      NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);

  commandCharacteristic->setCallbacks(new CommandCallbacks());
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

  lastPacketMs = millis();
}

void loop() {
  const uint32_t now = millis();
  if (bleServer != nullptr) {
    const bool connectedNow = bleServer->getConnectedCount() > 0;
    if (connectedNow && !isConnected) {
      onBleConnected("poll");
    } else if (!connectedNow && isConnected) {
      onBleDisconnected("poll");
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

  if (now - lastPacketMs > FAILSAFE_MS) {
    if (lastCommand.throttle != 0 || lastCommand.rudder != 0 || lastCommand.armed) {
      lastCommand.throttle = 0;
      lastCommand.rudder = 0;
      lastCommand.armed = false;
      lastCommand.stop = true;
      applyOutputs(lastCommand);
    }
  }
  delay(10);
}
