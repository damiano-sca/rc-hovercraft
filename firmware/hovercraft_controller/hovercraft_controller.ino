#include <Arduino.h>
#include <ESP32Servo.h>
#include <NimBLEDevice.h>

static const char *DEVICE_NAME = "Wobble Wagon";
static const char *SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB";
static const char *COMMAND_CHAR_UUID = "0000BEEF-0000-1000-8000-00805F9B34FB";
static const char *BATTERY_CHAR_UUID = "0000BABA-0000-1000-8000-00805F9B34FB";

static const uint8_t START_BYTE = 0xA5;
static const uint8_t END_BYTE = 0x5A;
static const uint8_t FLAG_ARM = 0x01;

static const uint32_t FAILSAFE_MS = 250;

// Battery voltage monitoring
static const int BATTERY_ADC_PIN = 15;

// ADC characteristics
static const float ADC_REF_VOLTAGE = 3.3f;
static const float ADC_MAX = 4095.0f;

// Voltage divider values (R1: battery -> pin, R2: pin -> GND)
static const float BATTERY_R1 = 10030.0f;
static const float BATTERY_R2 = 4350.0f;

// Battery voltage range (used in the Android app percent calculation).
static const float BATTERY_FULL_V = 8.40f;
static const float BATTERY_EMPTY_V = 6.60f;

// Battery print/notify interval
static const uint32_t BATTERY_PRINT_MS = 5000;

static const int THROTTLE_PWM_PIN = 17;
static const int THROTTLE_MIN_US = 1000;
static const int THROTTLE_MAX_US = 2000;

static const int RUDDER_PWM_PIN = 18;
static const uint8_t RUDDER_DISARMED_ANGLE = 0;
static const int RUDDER_MIN_US = 500;
static const int RUDDER_MAX_US = 2500;

static Servo throttleServo;
static Servo rudderServo;

struct CommandState {
  uint8_t throttle = 0;
  uint8_t rudderAngle = RUDDER_DISARMED_ANGLE;
  bool armed = false;
};

static CommandState lastCommand;
static uint32_t lastPacketMs = 0;


static uint32_t lastBatteryPrintMs = 0;
static NimBLECharacteristic *batteryCharacteristic = nullptr;


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

static float readBatteryVoltage() {
  int raw = analogRead(BATTERY_ADC_PIN);

  float vPin = (raw / ADC_MAX) * ADC_REF_VOLTAGE;
  float vBattery = vPin * ((BATTERY_R1 + BATTERY_R2) / BATTERY_R2);

  return vBattery;
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
  int rudderUs = map(rudderAngle, 0, 255, RUDDER_MIN_US, RUDDER_MAX_US);
  rudderServo.writeMicroseconds(rudderUs);
  
  Serial.print("Rudder angle: ");
  Serial.print(rudderAngle);
  Serial.print(" (");
  Serial.print(rudderUs);
  Serial.println(" us)");
}

static void applyOutputs(const CommandState &cmd) {
  applyThrottle(cmd);
  applyRudder(cmd);
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

// Initializes serial logging for startup and status messages.
static void initSerial() {
  Serial.begin(115200);
  Serial.println("Starting serial");
}

// Arms the ESC and sets the rudder to a known starting angle.
static void initServos() {
  throttleServo.attach(THROTTLE_PWM_PIN);
  throttleServo.writeMicroseconds(THROTTLE_MIN_US);
  rudderServo.attach(RUDDER_PWM_PIN, RUDDER_MIN_US, RUDDER_MAX_US);
  rudderServo.writeMicroseconds(map(RUDDER_DISARMED_ANGLE, 0, 255, RUDDER_MIN_US, RUDDER_MAX_US));
  Serial.println("Servos initialized");
}

// Configures the ADC input used to sample battery voltage.
static void initBatteryAdc() {
  analogReadResolution(12);        // 0–4095
  analogSetAttenuation(ADC_11db);  // Full 0–3.3V range
  Serial.println("Battery ADC initialized");
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

  batteryCharacteristic = service->createCharacteristic(
      BATTERY_CHAR_UUID,
      NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  float initialVoltage = 0.0f;
  batteryCharacteristic->setValue(reinterpret_cast<uint8_t *>(&initialVoltage), sizeof(initialVoltage));
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

void setup() {
  initSerial();
  initBatteryAdc();
  initServos();
  initBle();

  lastPacketMs = millis();
}

void loop() {
  if (millis() - lastPacketMs > FAILSAFE_MS) {
    // Only drive outputs if we need to return to the disarmed state.
    if (lastCommand.throttle != 0  || lastCommand.armed) {
      lastCommand.throttle = 0;
      lastCommand.armed = false;
      applyOutputs(lastCommand);
    }
  }

  // Periodic battery voltage print + BLE notification.
  if (millis() - lastBatteryPrintMs > BATTERY_PRINT_MS) {
    lastBatteryPrintMs = millis();

    float batteryVoltage = readBatteryVoltage();
    Serial.print("Battery voltage: ");
    Serial.print(batteryVoltage, 2);
    Serial.println(" V");

    if (batteryCharacteristic != nullptr) {
      float voltage = batteryVoltage;
      batteryCharacteristic->setValue(reinterpret_cast<uint8_t *>(&voltage), sizeof(voltage));
      batteryCharacteristic->notify();
    }
  }

  delay(10);
}
