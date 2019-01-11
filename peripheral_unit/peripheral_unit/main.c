/*
 * The main source file for ACGPanel peripheral unit
 *
 * Created: 21.12.2018 9.32.49
 * Author : Ilmo Euro <ilmo.euro@gmail.com>
 */ 

#include <avr/interrupt.h>
#include <avr/io.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include "main.h"

#define VALIDATE_BYTE(var, validator) \
  if (var == -1) { continue; } \
  if (var < 0x21) { return; } \
  if (!validator) { break; }

#define MAX_OLD_MESSAGES 10
#define RETRY_TIME 1000
#define MESSAGE_WAIT_TIME 200

#define START_OF_MESSAGE '\x02'
#define START_OF_MESSAGE_STR "\x02"
#define END_OF_MESSAGE '\n'
#define END_OF_MESSAGE_STR "\n"
#define SEPARATOR ';'
#define SEPARATOR_STR ";"

#define MESSAGE_TYPE_ACK 0
#define MESSAGE_TYPE_OPEN_LOCK 1
#define MESSAGE_TYPE_RFID 4

#define F_OSC 8000000
#define BAUD_RATE 9600

int16_t isBefore(int16_t messagenumber1, int16_t messagenumber2) {
  if (messagenumber2 < 0x4000) {
    return (messagenumber1 < messagenumber2) ||
           (messagenumber1 >= (messagenumber2 + 0x4000));
  } else {
    return (messagenumber1 < messagenumber2) &&
           (messagenumber1 >= (messagenumber2 - 0x4000));
  }
}

// USART routines
void cuCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE - 1;
  UBRR2H = (uint8_t)(ubrr>>8);
  UBRR2L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR2B = (1<<RXEN2) | (1<<TXEN2); // enable RX and TX
  UCSR2C = (1<<UCSZ20) | (1<<UCSZ21); // 8 data bits
}

void cuCommWrite(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR2A & (1<<UDRE2)))
    ;
  UDR2 = c;
}

/*
int16_t cuCommRead(void) {
  // 2549Q-AVR-02/2014 page 210
  if (!(UCSR2A & (1<<RXC2))) {
    return -1;
  }
  return UDR2;
}
*/

// TESTING VERSION
int16_t cuCommRead(void) {
  static char values[] = "\x02" "1;0;0;;51;\n";
  static int i = 0;
  return values[i++ % (sizeof values/sizeof values[0])];
}

// messaging routines
void cuCommWriteString(int16_t length, char *string) {
  for (int i=0; i<length; i++) {
    cuCommWrite((uint8_t)string[i]);
  }
}

void cuCommWriteChkSum(int16_t length, char *part, uint8_t *checksum) {
  for (int i=0; i<length; i++) {
    uint8_t c = (uint8_t)part[i];
    *checksum ^= c;
    cuCommWrite(c);
  }
}

void cuCommSendMsg(int16_t type, int16_t number, char* payload) {
  char typeStr[10];
  char numberStr[10];
  char payloadLengthStr[10];
  sprintf(typeStr, "%d", type);
  sprintf(numberStr, "%d", type);
  sprintf(payloadLengthStr, "%d", type);
  uint8_t checksum = 0;
  cuCommWriteChkSum(1, START_OF_MESSAGE_STR, &checksum);
  cuCommWriteChkSum(strlen(typeStr), typeStr, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  cuCommWriteChkSum(strlen(numberStr), numberStr, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  cuCommWriteChkSum(strlen(payloadLengthStr), payloadLengthStr, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  cuCommWriteChkSum(strlen(payload), payload, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  char checksumStr[10];
  sprintf(checksumStr, "%d", checksum);
  cuCommWriteString(strlen(checksumStr), checksumStr);
  cuCommWriteString(2, ";\n");
}

// timing routines
volatile uint16_t timer_left = 0u;

void timerInit(void) {
  uint16_t numCycles = F_OSC / 1000;
  uint8_t sreg = SREG;
  cli();
  OCR1AH = (uint8_t)(numCycles >> 8);
  OCR1AL = (uint8_t)numCycles;
  TCCR1A = 0;
  TCCR1B = (1 << WGM12) | (1 << CS10);
  TCNT1H = 0;
  TCNT1L = 0;
  TIMSK1 = 1 << OCIE1A;
  SREG = sreg;
}

ISR(TIMER1_COMPA_vect)
{
  if (timer_left != 0u) {
    --timer_left;
  }
}

void timerSet(uint16_t millis) {
  uint8_t sreg = SREG;
  cli();
  timer_left = millis;
  SREG = sreg;
}

int16_t timerFinished() {
  return timer_left == 0u;
}

void timerWait(uint16_t millis) {
  timerSet(millis);
  while (!timerFinished()) {
    // wait
  }
}

void init(void) {
  cuCommInit();
  timerInit();
  sei();
}

uint16_t lastMessageNumber = 0u;

void loop(void) {
  uint8_t checksum = 0;
  int16_t byte = 0;

  // start of message
  timerSet(MESSAGE_WAIT_TIME);
  while ((byte = cuCommRead()) != START_OF_MESSAGE) {
    if (timerFinished()) {
      cuCommWrite(0x00); // keep channel open
      return;
    }
  }
  checksum ^= byte;

  // message type
  int16_t messagetype = 0;
  while (1) {
    byte = cuCommRead();
    VALIDATE_BYTE(byte, isdigit(byte));
    checksum ^= byte;
    messagetype *= 10;
    messagetype += byte - '0';
  }
  checksum ^= byte;
  if (byte != SEPARATOR) return;

  // message number
  int16_t messagenumber = 0;
  while (1) {
    byte = cuCommRead();
    VALIDATE_BYTE(byte, isdigit(byte));
    checksum ^= byte;
    messagenumber *= 10;
    messagenumber += byte - '0';
  }
  checksum ^= byte;
  if (byte != SEPARATOR) return;

  // message length
  int16_t length = 0;
  while (1) {
    byte = cuCommRead();
    VALIDATE_BYTE(byte, isdigit(byte));
    checksum ^= byte;
    length *= 10;
    length += byte - '0';
  }
  checksum ^= byte;
  if (byte != SEPARATOR) return;

  // message payload
  int16_t payload = 0;
  for (int pos = 0; pos<length; pos++) {
    while ((byte = cuCommRead()) == -1);
    if (byte < 0x21) {
      return;
    }
    checksum ^= byte;
    if (messagetype == 0) {
      payload *= 10;
      payload += byte - '0';
    }
  }
  while ((byte = cuCommRead()) == -1);
  checksum ^= byte;
  if (byte != SEPARATOR) return;

  // checksum
  int16_t refChecksum = 0;
  while (1) {
    byte = cuCommRead();
    VALIDATE_BYTE(byte, isdigit(byte));
    refChecksum *= 10;
    refChecksum += byte - '0';
  }
  if (checksum != refChecksum || byte != SEPARATOR) return;

  // end of message
  while ((byte = cuCommRead()) == -1);
  if (byte != END_OF_MESSAGE) return;

  // acknowledge
  if (messagetype != 0) {
    char payload[10];
    sprintf(payload, "%d", messagenumber);
    lastMessageNumber = (messagenumber+1) & 0x7FFF;
    for (int i=0; i<5; i++) {
      cuCommSendMsg(0, lastMessageNumber, payload);
      timerWait(100);
    }
  }
}

int16_t main(void)
{
  init();
  while (1) 
  {
    loop();
  }
  return 0;
}

