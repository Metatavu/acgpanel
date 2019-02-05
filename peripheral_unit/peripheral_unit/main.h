#ifndef MAIN_H
#define MAIN_H

int16_t isBefore(int16_t messagenumber1, int16_t messagenumber2);

// USART routines
void cuCommInit(void);
void cuCommWrite(uint8_t c);
int16_t cuCommRead(void);

// timing routines
void timerInit(void);
void timerSet(uint16_t millis);
int16_t timerFinished();
void timerWait(uint16_t millis);

void init(void);
void loop(void);
#endif