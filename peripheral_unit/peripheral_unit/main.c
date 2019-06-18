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
#define TARGET_LIGHTS 4

#define MESSAGE_WAIT_TIME 200

#define F_OSC 8000000
#define BAUD_RATE_CU 9600
#define BAUD_RATE_BD 2400
#define BAUD_RATE_CR 9600

#define MS 25

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

#define BD_RING_BUFFER_LENGTH 256
uint8_t bdRingBuffer[BD_RING_BUFFER_LENGTH];
uint8_t *pBdRingBufferEnd;
uint8_t *pBdRingBufferRead;
uint8_t *pBdRingBufferWrite;
volatile int16_t rs485_enable_timer;

void bdCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE_BD - 1;
  UBRR1H = (uint8_t)(ubrr>>8);
  UBRR1L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR1B = (1<<RXEN1) | (1<<TXEN1) | (1<<RXCIE1); // enable RX and TX
  UCSR1C = (1<<UCSZ10) | (1<<UCSZ11); // 8 data bits
  DDRC |= 1 << PC0;
  pBdRingBufferEnd = bdRingBuffer + BD_RING_BUFFER_LENGTH;
  pBdRingBufferRead = bdRingBuffer;
  pBdRingBufferWrite = bdRingBuffer;
}

void bdCommWrite(uint8_t c) {
  *pBdRingBufferWrite++ = c;
  if (pBdRingBufferWrite == pBdRingBufferRead) {
    pBdRingBufferWrite--;
  }
  if (pBdRingBufferWrite > pBdRingBufferEnd) {
    pBdRingBufferWrite = bdRingBuffer;
  }
}

void bdCommWriteUnbuffered(uint8_t c) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR1A & (1<<UDRE1))) {
  }
  UDR1 = c;
}

void bdCommUnBuffer() {
  if (pBdRingBufferRead == pBdRingBufferWrite) {
    return;
  }
  bdCommWriteUnbuffered(*pBdRingBufferRead++);
  rs485_enable_timer = 10*MS;
  if (pBdRingBufferRead > pBdRingBufferEnd) {
    pBdRingBufferRead = bdRingBuffer;
  }
}

void bdCommFlush(void) {
  // 2549Q-AVR-02/2014 page 207
  while (!(UCSR1A & (1<<UDRE1))) {
  }
}

void bdCommInput(void) {
  PORTC &= ~(1<<PC0);
}

void bdCommOutput(void) {
  PORTC |= (1<<PC0);
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

void wdtInit(void) {
  uint8_t sreg = SREG;
  cli();
  WDTCSR |= (1<<WDCE);
  WDTCSR &= ~((1<<WDP2) | (1<<WDP1));
  WDTCSR |= (1<<WDP3) | (1<<WDP0); // 1 second reset timeout
  SREG = sreg;
}

typedef void (*Function)(void);

void reset(void) {
  Function bootloader;
  bootloader = (Function)0x7E00;
  bootloader();
}

void wieInit(void) {
}

void timerInit(void) {
#ifdef TEST
  uint16_t numCycles = 100;
#else
  // 25kHz timer
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

uint16_t pwmCurrent = 0;
uint16_t pwmValue = 0;
uint16_t pwmCounter = 0;

void pwmInit(void) {
  DDRC |= (1 << PC2) | (1 << PC3);
}

void pwmSetValue(uint16_t value) {
  pwmValue = value;
}

__attribute__((always_inline)) inline void pwmTick() {
  ++pwmCounter;
  // there is a source of 50Hz noise on board,
  // use 499 to minimize its impact
  if (pwmCounter > 499) {
    pwmCounter = 0;
	if (pwmCurrent < pwmValue) {
		pwmCurrent += 5;
	}
	else if (pwmCurrent > pwmValue) {
		pwmCurrent -= 5;
	}
  }
  if (pwmCounter < pwmCurrent) {
    PORTC |= (1 << PC2) | (1 << PC3);
  } else {
    PORTC &= ~((1 << PC2) | (1 << PC3));
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
  pwmTick();
}

ISR(USART2_RX_vect) {
  uint8_t input = UDR2;
  if (target_timer <= 0) {
    target_timer = 100*MS;
    target = input;
  } else {
    switch (target) {
      case 0:
        {
          switch (input) {
            case 0:
              cuCommWrite(0);
              break;
            case 1:
              reset();
              break;
          }
        }
        break;
      case TARGET_BOX_DRIVER:
        bdCommOutput();
        bdCommWrite(input);
        // the actual delay will be set when unbuffering
        // see bdCommUnBuffer()
        rs485_enable_timer = 100*MS;
        break;
      case TARGET_CARD_READER:
        crCommWrite(input);
        break;
      case TARGET_LIGHTS:
        if (input == 0) {
          pwmSetValue(0);
        } else {
          pwmSetValue(input * 5);
        }
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

const uint32_t WIEGAND_IDLE_TIME = 20000;
uint8_t wieBuffer[128] = {};

uint8_t wieReadValue() {
  if ((PINE & (1<<PE2)) && !(PINE & (1<<PE3))) {
    return 0x01;
  } else if (!(PINE & (1<<PE2)) && (PINE & (1<<PE3))) {
    return 0x00;
  } else {
    return 0xFF;
  }
}
  
void wieRead(void) {    
  uint8_t wieCounter = 0;
  uint8_t value = wieReadValue();
  if (value == 0xFF) {
    return;
  }
  while (wieCounter < 128) {
    while (wieReadValue() != 0xFF);
    wieBuffer[wieCounter++] = '0' + value;
    uint32_t idleTime = 0;
    while ((value = wieReadValue()) == 0xFF) {
      wdt_reset();
      if (idleTime++ > WIEGAND_IDLE_TIME) {
        goto codeRead;
      }
    }
  }
codeRead:
  cuCommWrite(TARGET_WIEGAND);
  for (uint8_t i=0; i<wieCounter; i++) {
    wdt_reset();
    cuCommWrite(wieBuffer[i]);
  }
}

void init(void) {
  wdtInit();
  wieInit();
  cuCommInit();
  bdCommInit();
  crCommInit();
  timerInit();
  pwmInit();
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

int16_t main(void)
{
  pass();
}  

#else // TEST

int16_t main(void)
{
  init();
  while (1) 
  {
    wieRead();
    bdCommUnBuffer();
    wdt_reset();
  }
  return 0;
}

#endif // not TEST

