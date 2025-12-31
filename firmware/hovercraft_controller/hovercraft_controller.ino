#include <Arduino.h>
#include <NimBLEDevice.h>

static const char *DEVICE_NAME = "Hovercraft-ESP32";
static const char *SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB";
static const char *COMMAND_CHAR_UUID = "0000BEEF-0000-1000-8000-00805F9B34FB";

static const uint8_t START_BYTE = 0xA5;
static const uint8_t END_BYTE = 0x5A;
static const uint8_t FLAG_ARM = 0x01;
static const uint8_t FLAG_STOP = 0x02;

static const uint32_t FAILSAFE_MS = 250;

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

struct CommandState {
  uint8_t seq = 0;
  uint8_t throttle = 0;
  int8_t rudder = 0;
  bool armed = false;
  bool stop = false;
};

static CommandState lastCommand;
static uint32_t lastPacketMs = 0;

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
  ledcWrite(THROTTLE_PWM_CHANNEL, throttleDuty);

  if (useRudderPwm) {
    int pulseUs = map(rudder, -100, 100, RUDDER_MIN_US, RUDDER_MAX_US);
    uint32_t duty = usToDuty(pulseUs, RUDDER_PWM_FREQ, RUDDER_PWM_RES);
    ledcWrite(RUDDER_PWM_CHANNEL, duty);
  }
}

class CommandCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic *characteristic) override {
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

    lastCommand = cmd;
    lastPacketMs = millis();
    applyOutputs(lastCommand);
  }
};

void setup() {
  Serial.begin(115200);

  ledcSetup(THROTTLE_PWM_CHANNEL, THROTTLE_PWM_FREQ, THROTTLE_PWM_RES);
  ledcAttachPin(THROTTLE_PWM_PIN, THROTTLE_PWM_CHANNEL);

  ledcSetup(RUDDER_PWM_CHANNEL, RUDDER_PWM_FREQ, RUDDER_PWM_RES);
  ledcAttachPin(RUDDER_PWM_PIN, RUDDER_PWM_CHANNEL);

  NimBLEDevice::init(DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);

  NimBLEServer *server = NimBLEDevice::createServer();
  NimBLEService *service = server->createService(SERVICE_UUID);
  NimBLECharacteristic *commandCharacteristic = service->createCharacteristic(
      COMMAND_CHAR_UUID,
      NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);

  commandCharacteristic->setCallbacks(new CommandCallbacks());
  service->start();

  NimBLEAdvertising *advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  lastPacketMs = millis();
}

void loop() {
  if (millis() - lastPacketMs > FAILSAFE_MS) {
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
