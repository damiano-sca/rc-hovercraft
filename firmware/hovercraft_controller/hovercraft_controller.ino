#include <Arduino.h>
#include <ESP32Servo.h>
#include <NimBLEDevice.h>

static const char *DEVICE_NAME = "Wobble Wagon";
static const char *SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB";
static const char *COMMAND_CHAR_UUID = "0000BEEF-0000-1000-8000-00805F9B34FB";

static const uint8_t START_BYTE = 0xA5;
static const uint8_t END_BYTE = 0x5A;
static const uint8_t FLAG_ARM = 0x01;

static const uint32_t FAILSAFE_MS = 250;

static const int THROTTLE_PWM_PIN = 7;
static const uint32_t THROTTLE_PWM_FREQ = 2000;
static const uint8_t THROTTLE_PWM_RES = 8;

static const int RUDDER_PWM_PIN = 8;
static const int RUDDER_MIN_US = 1000;
static const int RUDDER_MAX_US = 2000;
static const uint8_t RUDDER_MIN_ANGLE = 20;
static const uint8_t RUDDER_MAX_ANGLE = 160;
static const uint8_t RUDDER_CENTER_ANGLE = 90;

static Servo rudderServo;
static int throttlePwmChannel = 0;

struct CommandState {
  uint8_t throttle = 0;
  uint8_t rudderAngle = RUDDER_CENTER_ANGLE;
  bool armed = false;
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

static void applyOutputs(const CommandState &cmd) {
  uint8_t throttle = cmd.armed ? cmd.throttle : 0;
  uint8_t rudderAngle = cmd.armed ? cmd.rudderAngle : RUDDER_CENTER_ANGLE;

  uint32_t throttleDuty = map(throttle, 0, 100, 0, 255);
  ledcWrite(throttlePwmChannel, throttleDuty);

  rudderAngle = constrain(rudderAngle, RUDDER_MIN_ANGLE, RUDDER_MAX_ANGLE);
  rudderServo.write(rudderAngle);
  Serial.print("Rudder angle: ");
  Serial.println(rudderAngle);
}

class ServerCallbacks : public NimBLEServerCallbacks {
 public:
  void onConnect(NimBLEServer *server) {
    (void)server;
    Serial.println("BLE: connected");
  }

  void onDisconnect(NimBLEServer *server) {
    (void)server;
    Serial.println("BLE: disconnected");
    NimBLEDevice::getAdvertising()->start();
  }

  void onConnect(NimBLEServer *server, ble_gap_conn_desc *desc) {
    (void)server;
    (void)desc;
    Serial.println("BLE: connected");
  }

  void onDisconnect(NimBLEServer *server, ble_gap_conn_desc *desc) {
    (void)server;
    (void)desc;
    Serial.println("BLE: disconnected");
    NimBLEDevice::getAdvertising()->start();
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
    cmd.throttle = data[2];
    cmd.rudderAngle = static_cast<uint8_t>(data[3]);
    cmd.armed = (data[4] & FLAG_ARM) != 0;

    Serial.print("CMD: seq=");
    Serial.print(data[1]);
    Serial.print(" throttle=");
    Serial.print(cmd.throttle);
    Serial.print(" rudderAngle=");
    Serial.print(cmd.rudderAngle);
    Serial.print(" arm=");
    Serial.println(cmd.armed ? "1" : "0");

    lastCommand = cmd;
    lastPacketMs = millis();
    applyOutputs(lastCommand);
  }
};

void setup() {
  Serial.begin(115200);
  Serial.println("Starting");

  //throttlePwmChannel = ledcAttach(THROTTLE_PWM_PIN, THROTTLE_PWM_FREQ, THROTTLE_PWM_RES);
  rudderServo.attach(RUDDER_PWM_PIN);
  rudderServo.write(0);

  NimBLEDevice::init(DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);
  Serial.println("BLE: init");

  NimBLEServer *server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  NimBLEService *service = server->createService(SERVICE_UUID);
  NimBLECharacteristic *commandCharacteristic = service->createCharacteristic(
      COMMAND_CHAR_UUID,
      NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);

  commandCharacteristic->setCallbacks(new CommandCallbacks());
  service->start();

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
  if (millis() - lastPacketMs > FAILSAFE_MS) {
    if (lastCommand.throttle != 0 || lastCommand.rudderAngle != RUDDER_CENTER_ANGLE || lastCommand.armed) {
      lastCommand.throttle = 0;
      lastCommand.rudderAngle = RUDDER_CENTER_ANGLE;
      lastCommand.armed = false;
      applyOutputs(lastCommand);
    }
  }
  delay(10);
}
