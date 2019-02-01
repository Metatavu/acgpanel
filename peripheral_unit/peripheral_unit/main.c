/*
 * The main source file for ACGPanel peripheral unit
 *
 * Created: 21.12.2018 9.32.49
 * Author : Ilmo Euro <ilmo.euro@gmail.com>
 */ 

#include <avr/interrupt.h>
#include <avr/wdt.h>
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

#define START_OF_CU_MESSAGE '\x02'
#define START_OF_BD_MESSAGE '\x02'
#define START_OF_MESSAGE_STR "\x02"
#define END_OF_MESSAGE '\n'
#define END_OF_MESSAGE_STR "\n"
#define SEPARATOR ';'
#define SEPARATOR_STR ";"

#define MESSAGE_TYPE_ACK 0
#define MESSAGE_TYPE_OPEN_LOCK 1
#define MESSAGE_TYPE_RFID 4

#define F_OSC 8000000
#define BAUD_RATE_CU 9600
#define BAUD_RATE_BD 2400
#define BAUD_RATE_CR 9600

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
  uint16_t ubrr = F_OSC/16/BAUD_RATE_CU - 1;
  UBRR2H = (uint8_t)(ubrr>>8);
  UBRR2L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR2B = (1<<RXEN2) | (1<<TXEN2); // enable RX and TX
  UCSR2C = (1<<UCSZ20) | (1<<UCSZ21); // 8 data bits
}

void bdCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE_BD - 1;
  UBRR1H = (uint8_t)(ubrr>>8);
  UBRR1L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR1B = (1<<RXEN1) | (1<<TXEN1); // enable RX and TX
  UCSR1C = (1<<UCSZ10) | (1<<UCSZ11); // 8 data bits
  DDRC = 0x01;
}

void crCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE_CR - 1;
  UBRR3H = (uint8_t)(ubrr>>8);
  UBRR3L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR3B = (1<<RXEN3) | (1<<TXEN3); // enable RX and TX
  UCSR3C = (1<<UCSZ30) | (1<<UCSZ31); // 8 data bits
}

#ifdef TEST

int16_t testOutputBufLoc = 0;
uint8_t testOutputBuf[1024];

void cuCommWrite(uint8_t c) {
  testOutputBuf[testOutputBufLoc++] = c;
  testOutputBuf[testOutputBufLoc] = 0;
}

int16_t testInputBufLen = 0;
int16_t testInputBufLoc = 0;
uint8_t testInputBuf[1024];

int16_t cuCommRead(void) {
  if (testInputBufLoc < testInputBufLen) {
    return testInputBuf[testInputBufLoc++];
  } else {
    return -1;
  }
}

#else // not TEST

void cuCommWrite(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR2A & (1<<UDRE2))) {
    wdt_reset();
  }
  UDR2 = c;
}

int16_t cuCommRead(void) {
  // 2549Q-AVR-02/2014 page 210
  if (!(UCSR2A & (1<<RXC2))) {
    return -1;
  }
  return UDR2;
}

#endif // not TEST

uint8_t wdtPause = 0;

void bdCommWrite(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR1A & (1<<UDRE1))) {
    if (!wdtPause) {
      wdt_reset();
    }
  }
  UDR1 = c;
}

void bdCommFlush(void) {
  timerSet(1000);
  while (!(UCSR1A & (1<<UDRE1))) {
    if (timerFinished()) {
      return;
    }
  }    
}

int16_t bdCommRead(void) {
  // 2549Q-AVR-02/2014 page 210
  if (!(UCSR1A & (1<<RXC1))) {
    return -1;
  }
  return UDR1;
}

int16_t bdCommReadBlocking(void) {
  timerSet(1000);
  while (!(UCSR1A & (1<<RXC1))) {
    if (!wdtPause) {
      wdt_reset();
    }
    if (timerFinished()) {
      return -1;
    }
  }
  return UDR1;
}

int16_t bdCommReadShort(void) {
  timerSet(100);
  while (!(UCSR1A & (1<<RXC1))) {
    if (!wdtPause) {
      wdt_reset();
    }
    if (timerFinished()) {
      return -1;
    }
  }
  return UDR1;
}

void bdCommInput(void) {
  PORTC = 0x00;
}

void bdCommOutput(void) {
  PORTC = 0x01;
}

void crCommWrite(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR3A & (1<<UDRE3))) {
    wdt_reset();
  }
  UDR3 = c;
}

void crCommFlush(void) {
  while (!(UCSR3A & (1<<UDRE3))) {
    wdt_reset();
  }    
}

int16_t crCommRead(void) {
  // 2549Q-AVR-02/2014 page 210
  if (!(UCSR3A & (1<<RXC3))) {
    return -1;
  }
  return UDR3;
}

int16_t crCommReadBlocking(void) {
  timerSet(200);
  while (!(UCSR3A & (1<<RXC3))) {
    wdt_reset();
    if (timerFinished()) {
      return -1;
    }
  }
  return UDR3;
}

// messaging routines
void cuCommWriteString(int16_t length, char *string) {
  for (int i=0; i<length; i++) {
    cuCommWrite((uint8_t)string[i]);
  }
}

void cuCommWriteNullTerm(char *string) {
  int16_t length = strlen(string);
  cuCommWriteString(length, string);
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
  sprintf(numberStr, "%d", number);
  sprintf(payloadLengthStr, "%d", strlen(payload));
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
#ifdef TEST
  uint16_t numCycles = 100;
#else
  uint16_t numCycles = F_OSC / 1000;
#endif
  uint8_t sreg = SREG;
  cli();
  // compare value
  OCR1AH = (uint8_t)(numCycles >> 8);
  OCR1AL = (uint8_t)numCycles;
  // set mode: no prescaler, clear when reaching compare value
  TCCR1A = 0;
  TCCR1B = (1 << WGM12) | (1 << CS10);
  // start from 0
  TCNT1H = 0;
  TCNT1L = 0;
  // unmask compare interrupt
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
    wdt_reset();
  }
}

void init(void) {
  cuCommInit();
  bdCommInit();
  crCommInit();
  timerInit();
  sei();
}

int16_t sentMessageNumber = 0u;
int16_t receivedMessageNumber = 0u;
int16_t oldMessagesReceived = 0u;
uint8_t shelf = 0;
uint8_t compartment = 0;
uint8_t passthruBuffer[32];

void processBdMessage(void) {
  int16_t byte = 0;
  uint8_t msgShelf = 0;
  if ((byte = bdCommReadBlocking()) != -1) {
    msgShelf += byte - '0';
  }    
  if (byte == -1) {
    return;
  }
  if ((byte = bdCommReadBlocking()) != -1) {
    msgShelf *= 10;
    msgShelf += byte - '0';
  }
  if (byte == -1) {
    return;
  }
  if (msgShelf != shelf) {
    return;
  }
  if (bdCommReadBlocking() != 'R') {
    return;
  }
  if (bdCommReadBlocking() != 'E') {
    return;
  }
  while (bdCommReadBlocking() != -1) {
  }
  sentMessageNumber = (sentMessageNumber + 1) & 0x7FFF;
  char payload[10];
  sprintf(payload, "%u;%u", shelf, compartment);
  for (uint8_t i=0; i<3; i++) {
    cuCommSendMsg(5, sentMessageNumber, payload);
    timerWait(100);
  }

  for (int16_t i=0; i<6; i++) {
    int16_t bdByte;
    timerWait(100);
    bdCommOutput();
    timerWait(5);
    cuCommWrite(0x00); // keep CU comm open
    bdCommWrite(0x02); // STX
    bdCommWrite('0' + shelf / 10);
    bdCommWrite('0' + shelf % 10);
    bdCommWrite('R');
    bdCommWrite('E');
    bdCommWrite('S');
    bdCommWrite(0x0D); // CR
    bdCommFlush();
    timerWait(5);
    bdCommInput();
    if ((bdByte = bdCommReadBlocking()) != 0x02) {
      continue;
    }
    if (bdCommReadShort() != '0' + shelf / 10) continue;
    if (bdCommReadShort() != '0' + shelf % 10) continue;
    if (bdCommReadShort() != 'R') continue;
    if (bdCommReadShort() != 'S') continue;
    while (bdCommRead() != -1) {
      // don't reset watchdog
    }
    break;
  }
}

void processCuMessage(void) {
  uint8_t checksum = 0x02; // STX
  int16_t byte = 0x02;

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

  if (messagetype == 1) {
    shelf = 0;
    compartment = 0;
  }

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
  uint8_t part = 0;
  int16_t acknowledgedMessageNum = 0;
  int16_t passthruIndex = 0;
  for (int pos = 0; pos<length; pos++) {
    while ((byte = cuCommRead()) == -1);
    if (byte < 0x21) {
      return;
    }
    checksum ^= byte;
    // acknowledgement
    if (messagetype == 0) {
      acknowledgedMessageNum *= 10;
      acknowledgedMessageNum += byte - '0';
    }
    // open lock
    if (messagetype == 1) {
      if (byte == ';') {
        part = 1;
      } else if (part == 0) {
        shelf *= 10;
        shelf += byte - '0';
      } else {
        compartment *= 10;
        compartment += byte - '0';
      }
    }
    // RS485 passthru
    if (messagetype == 6) {
      passthruBuffer[passthruIndex++] = byte;
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

  /*
  if (messagenumber == receivedMessageNumber || 
      isBefore(messagenumber, receivedMessageNumber)) {
    if (oldMessagesReceived < MAX_OLD_MESSAGES) {
      oldMessagesReceived++;
      return;
    } else {
      oldMessagesReceived = 0;
      receivedMessageNumber = messagenumber;
    }
  }
  */

  // acknowledge
  if (messagetype != 0) {
    char payload[10];
    sprintf(payload, "%d", messagenumber);
    sentMessageNumber = (sentMessageNumber+1) & 0x7FFF;
    for (uint8_t i=0; i<5; i++) {
      cuCommSendMsg(0, sentMessageNumber, payload);
      timerWait(100);
    }
  }

  // open lock
  if (messagetype == 1) {
    for (int16_t i=0; i<5; i++) {
      int16_t bdByte;
      timerWait(100);
      bdCommOutput();
      timerWait(5);
      cuCommWrite(0x00); // keep CU comm open
      bdCommWrite(0x02); // STX
      bdCommWrite('0' + shelf / 10);
      bdCommWrite('0' + shelf % 10);
      bdCommWrite('O');
      bdCommWrite('P');
      bdCommWrite('E');
      bdCommWrite('0');
      bdCommWrite('0' + compartment / 10);
      bdCommWrite('0' + compartment % 10);
      bdCommWrite(0x0D); // CR
      bdCommFlush();
      timerWait(5);
      bdCommInput();
      if ((bdByte = bdCommReadBlocking()) != 0x02) continue;
      if (bdCommReadShort() != '0' + shelf / 10) continue;
      if (bdCommReadShort() != '0' + shelf % 10) continue;
      if (bdCommReadShort() != 'O') continue;
      if (bdCommReadShort() != 'K') continue;
      if (bdCommReadShort() != 'O') continue;
      while (bdCommRead() != -1) {
        // don't reset watchdog
      }
      break;
    }
  }

  // passthru message
  if (messagetype == 6) {
    int16_t bdByte;
    timerWait(100);
    bdCommOutput();
    timerWait(5);
    bdCommWrite(0x02); // STX
    for (int i=0; i<passthruIndex; i++) {
      bdCommWrite(passthruBuffer[i]);
    }
    bdCommWrite(0x0D); // CR
    bdCommFlush();
    timerWait(5);
    bdCommInput();
    while ((bdByte = bdCommReadBlocking()) != -1) {
      cuCommWrite(bdByte);
    }
  }
}

void processCrMessage(void) {
  int16_t byte = -1;
  char code[40];
  int i = 0;
  while ((byte = crCommReadBlocking()) != -1 && byte != '\n') {
    if (i < 39) {
      code[i++] = byte;
    }
  }
  code[i] = '\0';
  while (crCommRead() != -1);
  sentMessageNumber = (sentMessageNumber+1) & 0x7FFF;
  for (uint8_t i=0; i<5; i++) {
    cuCommSendMsg(4, sentMessageNumber, code);
    timerWait(100);
  }
}

void loop(void) {
  timerSet(MESSAGE_WAIT_TIME);
  while (!timerFinished()) {
    int16_t byte;
    if (bdCommRead() == START_OF_BD_MESSAGE) {
      processBdMessage();
    }
    if ((byte = cuCommRead()) == START_OF_CU_MESSAGE) {
      processCuMessage();
    }
    if (byte == 0x03) {
      for (;;); // reset
    }
    if ((byte = crCommRead()) != -1) {
      processCrMessage();
    }
    wdt_reset();
  }
  // keep channel open
  cuCommWrite(0x00);
}

#ifdef TEST

volatile char *failReason;

int16_t fail(void) {
  return 0; // PUT BREAKPOINT HERE
}

int16_t pass(void) {
  return 0; // PUT BREAKPOINT HERE
}

void testCuCommWriteString(void) {
  testOutputBufLoc = 0;
  cuCommWriteString(1, "x");
  if (strcmp((char *)testOutputBuf, "x") != 0) {
    failReason = "Didn't write \"x\" to output buffer";
    fail();
  }
  testOutputBufLoc = 0;
  cuCommWriteString(2, "\x01\x02");
  if (strcmp((char *)testOutputBuf, "\x01\x02") != 0) {
    failReason = "Didn't write \"\\x01\\x02\" to output buffer";
    fail();
  }
}

void testAsyncTimer(void) {
  timerSet(1000);
  if (timer_left == 0) {
    failReason = "Timer zero after timerSet";
    fail();
  }
  for (int i=0; i<1000; i++) {
    PORTA = PINB; // prevent optimization
  }
  if (timer_left == 1000) {
    failReason = "Timer not changed after wait";
    fail();
  }
  timerSet(1);
  for (int i=0; i<1000; i++) {
    PORTA = PINB; // prevent optimization
  }
  if (timer_left != 0) {
    failReason = "Short timer not finished after wait";
    fail();
  }
}

void testTimerFinished(void) {
  timerSet(0);
  if (!timerFinished()) {
    failReason = "Timer not finished after setting to 0";
    fail();
  }
  timerSet(10000);
  if (timerFinished()) {
    failReason = "Timer finished immediately after starting";
    fail();
  }
}

void testLoop(void) {
  strcpy((char*)testInputBuf, "\x02" "1;0;0;;51;\n");
  testInputBufLoc = 0;
  testInputBufLen = strlen((const char*)testInputBuf);
  sentMessageNumber = 0x7FFF;
  testOutputBufLoc = 0;
  loop();
  if (strncmp((const char *)testOutputBuf, "\x02" "0;1;1;0;2;\n", 12) != 0) {
    failReason = "Incorrect response from loop()";
    fail();
  }
}

void testKeepalive(void) {
  testOutputBufLoc = 0;
  loop();
  if (testOutputBufLoc != 1) {
    failReason = "No keep-alive byte sent";
    fail();
  }
  if (testOutputBuf[0] != '\0') {
    failReason = "Wrong keep-alive byte sent";
    fail();
  }
}

void testIsBefore(void) {
  if (!isBefore(0, 1)) {
    failReason = "isBefore(0, 1) fails";
    fail();
  }
  if (isBefore(1, 0)) {
    failReason = "isBefore(1, 0) fails";
    fail();
  }
  if (!isBefore(0, 0x3999)) {
    failReason = "isBefore(0, 0x3999) fails";
    fail();
  }
  if (isBefore(0, 0x4001)) {
    failReason = "isBefore(0, 0x4001) fails";
    fail();
  }
  if (!isBefore(0x3999, 0x4000)) {
    failReason = "isBefore(0x3999, 0x4000) fails";
    fail();
  }
  if (!isBefore(0x7FFF,  0)) {
    failReason = "isBefore(0x7FFF, 0) fails";
    fail();
  }
  if (!isBefore(0x3000, 0x5000)) {
    failReason = "isBefore(0x3000, 0x5000) fails";
    fail();
  }
}

int16_t main(void)
{
  init();
  testCuCommWriteString();
  testAsyncTimer();
  testTimerFinished();
  testIsBefore();
  testLoop();
  testKeepalive();
  pass();
  return 0;
}

#else // TEST

int16_t main(void)
{
  init();
  while (1) 
  {
    loop();
  }
  return 0;
}

#endif // not TEST

