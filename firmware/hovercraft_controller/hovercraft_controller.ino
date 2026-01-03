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
static const int THROTTLE_MIN_US = 1000;
static const int THROTTLE_MAX_US = 2000;

static const int RUDDER_PWM_PIN = 8;
static const uint8_t RUDDER_DISARMED_ANGLE = 0;

static Servo throttleServo;
static Servo rudderServo;

struct CommandState {
  uint8_t throttle = 0;
  uint8_t rudderAngle = RUDDER_DISARMED_ANGLE;
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

static void applyThrottle(const CommandState &cmd) {
  uint8_t throttle = cmd.armed ? cmd.throttle : 0;

  int throttleUs = map(throttle, 0, 100, THROTTLE_MIN_US, THROTTLE_MAX_US);
  throttleServo.writeMicroseconds(throttleUs);

  Serial.print("Throttle microseconds: ");
  Serial.println(throttleUs);
}

static void applyRudder(const CommandState &cmd) {
  uint8_t rudderAngle = cmd.armed ? cmd.rudderAngle : RUDDER_DISARMED_ANGLE;
  rudderServo.write(rudderAngle);
  
  Serial.print("Rudder angle: ");
  Serial.println(rudderAngle);
}

static void applyOutputs(const CommandState &cmd) {
  applyThrottle(cmd);
  applyRudder(cmd);
}

// Initializes serial logging for startup and status messages.
static void initSerial() {
  Serial.begin(115200);
  Serial.println("Starting serial");
}

// Arms the ESC and sets the rudder to a known starting angle.
static void initServos() {
  throttleServo.attach(THROTTLE_PWM_PIN);
  throttleServo.writeMicroseconds(THROTTLE_MIN_US);
  rudderServo.attach(RUDDER_PWM_PIN);
  rudderServo.write(RUDDER_DISARMED_ANGLE);
  Serial.println("Servos initialized");
}

// Sets up BLE services, characteristics, and advertising.
static void initBle() {
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
    // 1) Read the raw payload.
    std::string value = characteristic->getValue();
    // 2) Validate packet length.
    if (value.size() != 8) {
      return;
    }
    // 3) Validate framing bytes before parsing content.
    const uint8_t *data = reinterpret_cast<const uint8_t *>(value.data());
    if (data[0] != START_BYTE || data[7] != END_BYTE) {
      return;
    }
    // 4) Validate CRC over the header + payload bytes.
    uint8_t expectedCrc = crc8(data, 6);
    if (data[6] != expectedCrc) {
      return;
    }

    // 5) Decode fields into a command struct.
    CommandState cmd;
    cmd.throttle = data[2];
    cmd.rudderAngle = static_cast<uint8_t>(data[3]);
    cmd.armed = (data[4] & FLAG_ARM) != 0;

    // 6) Apply the command and reset the failsafe timer.
    lastCommand = cmd;
    lastPacketMs = millis();
    applyOutputs(lastCommand);
  }
};

void setup() {
  initSerial();
  initServos();
  initBle();

  lastPacketMs = millis();
}

void loop() {
  if (millis() - lastPacketMs > FAILSAFE_MS) {
    // Only drive outputs if we need to return to the disarmed state.
    if (lastCommand.throttle != 0 || lastCommand.rudderAngle != RUDDER_DISARMED_ANGLE || lastCommand.armed) {
      lastCommand.throttle = 0;
      lastCommand.rudderAngle = RUDDER_DISARMED_ANGLE;
      lastCommand.armed = false;
      applyOutputs(lastCommand);
    }
  }
  delay(10);
}
