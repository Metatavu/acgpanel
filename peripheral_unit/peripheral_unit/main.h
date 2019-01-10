#ifndef MAIN_H
#define MAIN_H

int isBefore(int messagenumber1, int messagenumber2);

// USART routines
void cuCommInit(void);
void cuCommWrite(int c);
int cuCommRead(void);

// messaging routines
void cuCommWriteString(int length, char *string);
void cuCommWriteChkSum(int length, char *part, int *checksum);
void cuCommSendMsg(int type, int number, char* payload);

// timing routines
void timerInit(void);
unsigned long timerMillisSinceBoot(void);
void timerWait(unsigned long millis);

void init(void);
void loop(void);
#endif