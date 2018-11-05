#include "EEPROM.h"

#define VALIDATE_BYTE(var, validator) \
  if (var == -1) { continue; } \
  if (var < 0x21) { return; } \
  if (!validator) { break; }

#define MAX_OLD_MESSAGES 100

int numOldMessages = 0;
int lastSentMessageNumber = 0;
bool acknowledged = false;

void writeWithChecksum(String part, int *checksum) {
  for (int i=0; i<part.length(); i++) {
    char c = part.charAt(i);
    *checksum ^= c;
    Serial.write(c);
  }
}

void sendMessage(int type, int number, String payload) {
  String type_str(type);
  String number_str(number);
  String payload_length_str(payload.length());

  int checksum = 0;
  writeWithChecksum("\x02", &checksum);
  writeWithChecksum(type_str, &checksum);
  writeWithChecksum(";", &checksum);
  writeWithChecksum(number_str, &checksum);
  writeWithChecksum(";", &checksum);
  writeWithChecksum(payload_length_str, &checksum);
  writeWithChecksum(";", &checksum);
  writeWithChecksum(payload, &checksum);
  writeWithChecksum(";", &checksum);

  Serial.print(checksum);
  Serial.print(";\n");
}

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(9600);
  // TODO only for debugging
  EEPROM.write(0, 0);
  EEPROM.write(1, 0);
}

void loop() {
  int byte;
  int checksum = 0;

  // start of message
  while ((byte = Serial.read()) != '\x02');
  checksum ^= byte;

  // message type
  int messagetype = 0;
  while (true) {
    byte = Serial.read();
    VALIDATE_BYTE(byte, isdigit(byte));
    checksum ^= byte;
    messagetype *= 10;
    messagetype += byte - '0';
  }
  checksum ^= byte;
  if (byte != ';') return;

  // message number
  int messagenumber = 0;
  while (true) {
    byte = Serial.read();
    VALIDATE_BYTE(byte, isdigit(byte));
    checksum ^= byte;
    messagenumber *= 10;
    messagenumber += byte - '0';
  }
  checksum ^= byte;
  if (byte != ';') return;

  // message length
  int length = 0;
  while (true) {
    byte = Serial.read();
    VALIDATE_BYTE(byte, isdigit(byte));
    checksum ^= byte;
    length *= 10;
    length += byte - '0';
  }
  checksum ^= byte;
  if (byte != ';') return;

  // message payload
  int payload = 0;
  for (int pos = 0; pos<length; pos++) {
    while ((byte = Serial.read()) == -1);
    if (byte < 0x21) {
      return;
    }
    checksum ^= byte;
    if (messagetype == 0) {
      payload *= 10;
      payload += byte - '0';
    }
  }
  while ((byte = Serial.read()) == -1);
  checksum ^= byte;
  if (byte != ';') return;

  // checksum
  int refChecksum = 0;
  while (true) {
    byte = Serial.read();
    VALIDATE_BYTE(byte, isdigit(byte));
    refChecksum *= 10;
    refChecksum += byte - '0';
  }
  if (checksum != refChecksum || byte != ';') return;

  // end of message
  while ((byte = Serial.read()) == -1);
  if (byte != 0x0A) return;

  int prevMessagenumber = EEPROM.read(0) << 8 + EEPROM.read(1);
  if (messagenumber < prevMessagenumber && numOldMessages++ < MAX_OLD_MESSAGES) {
    return;
  }
  numOldMessages = 0;

  // now we have a proper message
  int nextMessagenumber = (messagenumber + 1) & 0xFFFF;
  EEPROM.write(0, (nextMessagenumber & 0xFF00) >> 8);
  EEPROM.write(1, nextMessagenumber & 0xFF);

  // every received message needs to be acknowledged
  String acknowledgementPayload(messagenumber);
  sendMessage(0, nextMessagenumber, acknowledgementPayload);

  if (messagetype == 0 && payload >= lastSentMessageNumber) {
    acknowledged = true;
  } else if (messagetype == 1) {
    // open a locker door
    digitalWrite(LED_BUILTIN, HIGH);
    delay(500);
    digitalWrite(LED_BUILTIN, LOW);
  }

}