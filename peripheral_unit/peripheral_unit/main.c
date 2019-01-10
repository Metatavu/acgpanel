/*
 * The main source file for ACGPanel peripheral unit
 *
 * Created: 21.12.2018 9.32.49
 * Author : Ilmo Euro <ilmo.euro@gmail.com>
 */ 

#define __AVR_ATmega1280__
#include <avr/interrupt.h>
#include <avr/io.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <time.h>
#include "main.h"

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

#define F_OSC 8000000
#define BAUD_RATE 9600

int isBefore(int messagenumber1, int messagenumber2) {
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
  unsigned int ubrr = F_OSC/16/BAUD_RATE - 1;
  UBRR2H = (unsigned char)(ubrr>>8);
  UBRR2L = (unsigned char)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR2B = (1<<RXEN2) | (1<<TXEN2); // enable RX and TX
  UCSR2C = (1<<UCSZ20) | (1<<UCSZ21); // 8 data bits
}

void cuCommWrite(int c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR2A & (1<<UDRE2)))
    ;
  UDR2 = (unsigned char)c;
}

int cuCommRead(void) {
  // 2549Q-AVR-02/2014 page 210
  if ((UCSR2A & (1<<RXC2))) {
    return -1;
  }
  return UDR2;
}

// messaging routines
void cuCommWriteString(int length, char *string) {
  for (int i=0; i<length; i++) {
    cuCommWrite(string[i]);
  }
}

void cuCommWriteChkSum(int length, char *part, int *checksum) {
  for (int i=0; i<length; i++) {
    char c = part[i];
    *checksum ^= c;
    cuCommWrite(c);
  }
}

void cuCommSendMsg(int type, int number, char* payload) {
  char type_str[10];
  char number_str[10];
  char payload_length_str[10];
  sprintf(type_str, "%d", type);
  sprintf(number_str, "%d", type);
  sprintf(payload_length_str, "%d", type);
  int checksum = 0;
  cuCommWriteChkSum(1, START_OF_MESSAGE_STR, &checksum);
  cuCommWriteChkSum(strlen(type_str), type_str, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  cuCommWriteChkSum(strlen(number_str), number_str, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  cuCommWriteChkSum(strlen(payload_length_str), payload_length_str, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  cuCommWriteChkSum(strlen(payload), payload, &checksum);
  cuCommWriteChkSum(1, SEPARATOR_STR, &checksum);
  char checksum_str[10];
  sprintf(checksum_str, "%d", checksum);
  cuCommWriteString(strlen(checksum_str), checksum_str);
  cuCommWriteString(2, ";\n");
}

// timing routines
static volatile unsigned long millis_count = 0;
static volatile unsigned long wait_millis_left = 0;

void timerInit(void) {
  unsigned int period = F_OSC / 1000;
  unsigned char sreg = SREG;
  cli();
  TCCR1A = WGM12; // Clear Timer on Compare
  TCNT1H = (unsigned char)(period << 8);
  TCNT1L = (unsigned char)period;
  SREG = sreg;
}

ISR(TIMER1_COMPA_vect)
{
  ++millis_count;
  if (wait_millis_left > 0) {
    --wait_millis_left;
  }
}

unsigned long timerMillisSinceBoot(void) {
  unsigned char sreg = SREG;
  unsigned long result;
  cli();
  result = millis_count;
  SREG = sreg;
  return result;
}

void timerWait(unsigned long millis) {
  unsigned char sreg = SREG;
  cli();
  wait_millis_left = millis;
  SREG = sreg;
  while (1) {
    if (wait_millis_left == 0) {
      return;
    }
  }
}

void init(void) {
  cuCommInit();
}

void loop(void) {
  unsigned long start = timerMillisSinceBoot();
  int checksum = 0;
  int byte;

  // start of message
  while ((byte = cuCommRead()) != START_OF_MESSAGE) {
    if (timerMillisSinceBoot() - start > MESSAGE_WAIT_TIME) {
      return;
    }
  }
  checksum ^= byte;

  // message type
  int messagetype = 0;
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
  int messagenumber = 0;
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
  int length = 0;
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
  int payload = 0;
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
  int refChecksum = 0;
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
    // update last message number
    // setLastMessageNumber((lastMessageNumber + 1) & 0x7FFF);
    for (int i=0; i<5; i++) {
      cuCommSendMsg(0, (messagenumber+1) & 0x7FFF, payload);
      timerWait(100);
    }
  }
}

int main(void)
{
  init();
  while (1) 
  {
    loop();
  }
}

