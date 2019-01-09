/*
 * The main source file for ACGPanel peripheral unit
 *
 * Created: 21.12.2018 9.32.49
 * Author : Ilmo Euro <ilmo.euro@gmail.com>
 */ 

#include <avr/interrupt.h>
#include <avr/io.h>
#include <avr/iom1280.h>
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

#define F_OSC 1843200
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
void usartInit(void) {
  // init UART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  unsigned int ubrr = F_OSC/16/BAUD_RATE - 1;
  UBRR0H = (unsigned char)(ubrr>>8);
  UBRR0L = (unsigned char)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR0B = (1<<RXEN0) | (1<<TXEN0); // enable RX and TX
  UCSR0C = 1<<UCSZ00 | 1<<UCSZ01; // 8 data bits
}

void write(int c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR0A & (1<<UDRE0)))
    ;
  UDR0 = (unsigned char)c;
}

int read(void) {
  // 2549Q-AVR-02/2014 page 210
  while (!(UCSR0A & (1<<RXC0)))
    ;
  return UDR0;
}

// timing routines
volatile unsigned long millis_count = 0;
volatile unsigned long wait_millis_left = 0;

ISR(TIMER1_COMPA_vect)
{
  ++millis_count;
  if (wait_millis_left > 0) {
    --wait_millis_left;
  }
}

unsigned long millis(void) {
  cli();
  unsigned long result;
  result = millis_count;
  sei();
  return result;
}

void wait(unsigned long millis) {
  cli();
  wait_millis_left = millis;
  sei();
  while (1) {
    if (wait_millis_left == 0) {
      return;
    }
  }
}

// messaging routines
void writeWithChecksum(int length, char *part, int *checksum) {
  for (int i=0; i<length; i++) {
    char c = part[i];
    *checksum ^= c;
    write(c);
  }
}

void sendMessage(int type, int number, char* payload) {
  char type_str[10];
  char number_str[10];
  char payload_length_str[10];
  sprintf(type_str, "%d", type);
  sprintf(number_str, "%d", type);
  sprintf(payload_length_str, "%d", type);

  int checksum = 0;
  writeWithChecksum(1, START_OF_MESSAGE_STR, &checksum);
  writeWithChecksum(strlen(type_str), type_str, &checksum);
  writeWithChecksum(1, SEPARATOR_STR, &checksum);
  writeWithChecksum(strlen(number_str), number_str, &checksum);
  writeWithChecksum(1, SEPARATOR_STR, &checksum);
  writeWithChecksum(strlen(payload_length_str), payload_length_str, &checksum);
  writeWithChecksum(1, SEPARATOR_STR, &checksum);
  writeWithChecksum(strlen(payload), payload, &checksum);
  writeWithChecksum(1, SEPARATOR_STR, &checksum);
  char checksum_str[10];
  sprintf(checksum_str, "%d", checksum);
  writeString(strlen(checksum_str), checksum_str);
  writeString(2, ";\n");
}

void writeString(int length, char *string) {
  for (int i=0; i<length; i++) {
    write(string[i]);
  }
}


void init(void) {
  usartInit();
}

void loop(void) {
  unsigned long start = millis();
  int checksum = 0;
  int byte;

  // start of message
  while ((byte = read()) != START_OF_MESSAGE) {
    if (millis() - start > MESSAGE_WAIT_TIME) {
      return;
    }
  }
  checksum ^= byte;

  // message type
  int messagetype = 0;
  while (1) {
    byte = read();
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
    byte = read();
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
    byte = read();
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
    while ((byte = read()) == -1);
    if (byte < 0x21) {
      return;
    }
    checksum ^= byte;
    if (messagetype == 0) {
      payload *= 10;
      payload += byte - '0';
    }
  }
  while ((byte = read()) == -1);
  checksum ^= byte;
  if (byte != SEPARATOR) return;

  // checksum
  int refChecksum = 0;
  while (1) {
    byte = read();
    VALIDATE_BYTE(byte, isdigit(byte));
    refChecksum *= 10;
    refChecksum += byte - '0';
  }
  if (checksum != refChecksum || byte != SEPARATOR) return;

  // end of message
  while ((byte = read()) == -1);
  if (byte != END_OF_MESSAGE) return;

  // acknowledge
  if (messagetype != 0) {
    char payload[10];
    sprintf(payload, "%d", messagenumber);
    // update last message number
    // setLastMessageNumber((lastMessageNumber + 1) & 0x7FFF);
    for (int i=0; i<5; i++) {
      sendMessage(0, (messagenumber+1) & 0x7FFF, payload);
      wait(100);
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

