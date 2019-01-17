/*
 * box_driver.c
 *
 * Created: 17.1.2019 10.04.26
 * Author : liste
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

#define F_OSC 8000000
#define BAUD_RATE 9600

void puCommInit(void) {
  // init USART, 8 data bits, 1 stop bit
  // 2549Q-AVR-02/2014 page 206
  uint16_t ubrr = F_OSC/16/BAUD_RATE - 1;
  UBRR1H = (uint8_t)(ubrr>>8);
  UBRR1L = (uint8_t)ubrr;
  // 2549Q-AVR-02/2014 page 221
  UCSR1B = (1<<RXEN1) | (1<<TXEN1); // enable RX and TX
  UCSR1C = (1<<UCSZ10) | (1<<UCSZ11); // 8 data bits
  DDRD = 0x01;
}

void puCommWrite(uint8_t c) {
  while (!(UCSR1A & (1<<UDRE1))) {
    wdt_reset();
  }
  UDR1 = c;
}

void puCommFlush(void) {
  while (!(UCSR1A & (1<<UDRE1))) {
    wdt_reset();
  }    
}

int16_t puCommRead(void) {
  if (!(UCSR1A & (1<<RXC1))) {
    return -1;
  }
  return UDR1;
}

int16_t puCommReadBlocking(void) {
  while (!(UCSR1A & (1<<RXC1))) {
    wdt_reset();
  }
  return UDR1;
}

void puCommInput(void) {
  PORTD = 0x00;
}

void puCommOutput(void) {
  PORTD = 0x01;
}

void init(void) {
  puCommInit();
  DDRA = 0xFF;
}  

int main(void)
{
  init();
  while (1) 
  {
    int16_t byte = puCommReadBlocking();
    if (byte < 8) {
      for (uint32_t i=0; i<0xFFFF; i++) {
        PORTA = 1<<byte;
      }
      PORTA = 0x00;
    }
  }
}

