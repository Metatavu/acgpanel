#include "EEPROM.h"

#define VALIDATE_BYTE(var, validator) \
  if (var == -1) { continue; } \
  if (var < 0x21) { return; } \
  if (!validator) { break; }

#define MAX_OLD_MESSAGES 10
#define RETRY_TIME 1000
#define MESSAGE_WAIT_TIME 200

#define START_OF_MESSAGE '!'
#define START_OF_MESSAGE_STR "!"
#define END_OF_MESSAGE '\n'
#define END_OF_MESSAGE_STR "\n"
#define SEPARATOR ';'
#define SEPARATOR_STR ";"

#define MESSAGE_TYPE_ACK 0
#define MESSAGE_TYPE_OPEN_LOCK 1
#define MESSAGE_TYPE_RFID 4

int numOldMessages = 0;
int lastMessageNumber = 0;
bool acknowledged = false;

int lastSentMessageNumber = 0;
unsigned long lastSentMessageTime = 0;

void setLastMessageNumber(int messagenumber) {
  lastMessageNumber = messagenumber;
  EEPROM.put(0, lastMessageNumber);
}

bool isBefore(int messagenumber1, int messagenumber2) {
  if (messagenumber2 < 0x4000) {
    return (messagenumber1 < messagenumber2) ||
           (messagenumber1 >= (messagenumber2 + 0x4000));
  } else {
    return (messagenumber1 < messagenumber2) &&
           (messagenumber1 >= (messagenumber2 - 0x4000));
  }
}

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
  writeWithChecksum(START_OF_MESSAGE_STR, &checksum);
  writeWithChecksum(type_str, &checksum);
  writeWithChecksum(SEPARATOR_STR, &checksum);
  writeWithChecksum(number_str, &checksum);
  writeWithChecksum(SEPARATOR_STR, &checksum);
  writeWithChecksum(payload_length_str, &checksum);
  writeWithChecksum(SEPARATOR_STR, &checksum);
  writeWithChecksum(payload, &checksum);
  writeWithChecksum(SEPARATOR_STR, &checksum);

  Serial.print(checksum);
  Serial.print(";\n");
}

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(9600);
  EEPROM.get(0, lastMessageNumber);
}

void loop() {
  unsigned long start = millis();

  // Phase 1: Send a message if necessary

  // When millis() reset, lastMessageNumber will have negative overflow,
  // so its value will be very large. The message is resent and nothing
  // bad happens
  if (start - lastSentMessageTime > RETRY_TIME) {
    lastSentMessageNumber = (lastMessageNumber + 1) & 0x7FFF;
    lastSentMessageTime = start;
    if (acknowledged) {
      Serial.write((uint8_t)0);
    } else {
      sendMessage(MESSAGE_TYPE_RFID, lastSentMessageNumber, "04006d0ba0");
    }
  }

  int byte;
  int checksum = 0;

  // Phase 2: Read in an incoming message

  // start of message
  while ((byte = Serial.read()) != START_OF_MESSAGE) {
    if (millis() - start > MESSAGE_WAIT_TIME) {
      return;
    }
  }
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
  if (byte != SEPARATOR) return;

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
  if (byte != SEPARATOR) return;

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
  if (byte != SEPARATOR) return;

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
  if (byte != SEPARATOR) return;

  // checksum
  int refChecksum = 0;
  while (true) {
    byte = Serial.read();
    VALIDATE_BYTE(byte, isdigit(byte));
    refChecksum *= 10;
    refChecksum += byte - '0';
  }
  if (checksum != refChecksum || byte != SEPARATOR) return;

  // end of message
  while ((byte = Serial.read()) == -1);
  if (byte != END_OF_MESSAGE) return;

  if (isBefore(messagenumber, lastMessageNumber) && numOldMessages++ < MAX_OLD_MESSAGES) {
    return;
  }
  numOldMessages = 0;

  // now we have a proper message
  setLastMessageNumber(messagenumber);

  // Phase 3: Process the incoming message

  // every received message except acknowledgement messages needs to be acknowledged
  if (messagetype != 0) {
    String acknowledgementPayload(messagenumber);
    setLastMessageNumber((lastMessageNumber + 1) & 0x7FFF);
    for (int i=0; i<5; i++) {
      sendMessage(0, lastMessageNumber, acknowledgementPayload);
      delay(100);
    }
  }

  if (messagetype == MESSAGE_TYPE_ACK && !isBefore(payload, lastSentMessageNumber)) {
    acknowledged = true;
  } else if (messagetype == MESSAGE_TYPE_OPEN_LOCK) {
    // open a locker door
    digitalWrite(LED_BUILTIN, HIGH);
    delay(500);
    digitalWrite(LED_BUILTIN, LOW);
  }

}