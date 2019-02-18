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

#define TARGET_BOX_DRIVER 1
#define TARGET_CARD_READER 2
#define TARGET_WIEGAND 3

#define MESSAGE_WAIT_TIME 200

#define F_OSC 8000000
#define BAUD_RATE_CU 9600
#define BAUD_RATE_BD 9600
#define BAUD_RATE_CR 9600

#define MS 10

volatile uint8_t lastTarget = 0;

// USART routines
void cuCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE_CU - 1;
  UBRR2H = (uint8_t)(ubrr>>8);
  UBRR2L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR2B = (1<<RXEN2) | (1<<TXEN2) | (1<<RXCIE2); // enable RX and TX
  UCSR2C = (1<<UCSZ20) | (1<<UCSZ21); // 8 data bits
}

void bdCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE_BD - 1;
  UBRR1H = (uint8_t)(ubrr>>8);
  UBRR1L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR1B = (1<<RXEN1) | (1<<TXEN1) | (1<<RXCIE1); // enable RX and TX
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
  UCSR3B = (1<<RXEN3) | (1<<TXEN3) | (1<<RXCIE3); // enable RX and TX
  UCSR3C = (1<<UCSZ30) | (1<<UCSZ31); // 8 data bits
}

void cuCommWrite(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR2A & (1<<UDRE2))) {
  }
  UDR2 = c;
}

void bdCommWrite(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR1A & (1<<UDRE1))) {
  }
  UDR1 = c;
}

void bdCommFlush(void) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR1A & (1<<UDRE1))) {
  }
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
  }
  UDR3 = c;
}

volatile uint8_t target = 0;
volatile int16_t source_timer = 0;
volatile int16_t target_timer = 0;
volatile int16_t rs485_enable_timer;

void wdtInit(void) {
  uint8_t sreg = SREG;
  cli();
  WDTCSR |= (1<<WDCE);
  WDTCSR &= ~((1<<WDP2) | (1<<WDP1));
  WDTCSR |= (1<<WDP3) | (1<<WDP0); // 1 second reset timeout
  SREG = sreg;
}

void wieInit(void) {
}

void timerInit(void) {
#ifdef TEST
  uint16_t numCycles = 100;
#else
  // 10kHz timer
  uint16_t numCycles = F_OSC / (MS * 1000);
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

int16_t wieLastVal = -1;

uint8_t wieBuffer = 0;
uint8_t wieCounter = 0;
uint8_t wieFlushTimer = 0;

void cuCommWriteNybbleHex(uint8_t val) {
  val = val & 0x0F;
  if (val < 10) {
    cuCommWrite('0' + val);
  } else {
    cuCommWrite('A' + (val - 10));
  }
}
  
void wieRead(void) {    
  int16_t value;
  // Wiegand
  if ((PINE & (1<<PE2)) && !(PINE & (1<<PE3))) {
    value = 1;
  } else if (!(PINE & (1<<PE2)) && (PINE & (1<<PE3))) {
    value = 0;
  } else {
    value = -1;
  }
  if (value == wieLastVal) {
    return;
  }
  wieLastVal = value;
  if (value == -1) {
    return;
  }
  wieFlushTimer = 8*MS;
  if (source_timer <= 0) {
    source_timer = 20*MS;
    cuCommWrite(TARGET_WIEGAND);
  }
  wieBuffer <<= 1;
  wieBuffer |= value;
  wieCounter++;
  if (wieCounter >= 4) {
    cuCommWriteNybbleHex(wieBuffer);
    wieCounter = 0;
    wieBuffer = 0;
  }
}

ISR(TIMER1_COMPA_vect)
{
  if (source_timer > 0) {
    source_timer--;
  }
  if (target_timer > 0) {
    target_timer--;
  }
  if (rs485_enable_timer > 0) {
    rs485_enable_timer--;
  } else {
    bdCommInput();
  }
  if (wieFlushTimer > 0) {
    wieFlushTimer--;
  } else {
    if (wieCounter != 0) {
      cuCommWriteNybbleHex(wieBuffer);
      wieCounter = 0;
      wieBuffer = 0;
    }
  }
  wieRead();
}

ISR(USART2_RX_vect) {
  if (target_timer <= 0) {
    target_timer = 100*MS;
    target = UDR2;
  } else {
    switch (target) {
      case 0:
        cuCommWrite(UDR2);
        break;
      case TARGET_BOX_DRIVER:
        bdCommOutput();
        bdCommWrite(UDR2);
        rs485_enable_timer = 5*MS;
        break;
      case TARGET_CARD_READER:
        crCommWrite(UDR2);
        break;
    }
  }
}

ISR(USART1_RX_vect) {
  uint8_t val = UDR1;
  // filter out spurious non-ASCII chars
  if (val >= 0x80) {
    return;
  }
  if (rs485_enable_timer > 0) {
    return;
  }
  if (source_timer <= 0) {
    source_timer = 100*MS;
    cuCommWrite(TARGET_BOX_DRIVER);
  }
  cuCommWrite(val);
}

ISR(USART3_RX_vect) {
  if (source_timer <= 0) {
    source_timer = 100*MS;
    cuCommWrite(TARGET_CARD_READER);
  }
  cuCommWrite(UDR3);
}

void init(void) {
  wdtInit();
  wieInit();
  cuCommInit();
  bdCommInit();
  crCommInit();
  timerInit();
  sei();
}

#ifdef TEST

volatile char *failReason;

int16_t fail(void) {
  return 0; // PUT BREAKPOINT HERE
}

int16_t pass(void) {
  return 0; // PUT BREAKPOINT HERE
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

int16_t main(void)
{
  init();
  testAsyncTimer();
  testTimerFinished();
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
    wdt_reset();
  }
  return 0;
}

#endif // not TEST

