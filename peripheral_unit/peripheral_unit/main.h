#ifndef MAIN_H
#define MAIN_H

int isBefore(int messagenumber1, int messagenumber2);

// USART routines
void usartInit(void);
void write(int c);
int read(void);

// timing routines
unsigned long millis(void);
void wait(unsigned long millis);

// messaging routines
void writeString(int length, char *string);
void writeWithChecksum(int length, char *part, int *checksum);
void sendMessage(int type, int number, char* payload);

void init(void);
void loop(void);
#endif