#ifndef MAIN_H
#define MAIN_H

int isBefore(int messagenumber1, int messagenumber2);
void write(int c);
void writeString(int length, char *string);
void writeWithChecksum(int length, char *part, int *checksum);
void sendMessage(int type, int number, char* payload);
int read();
void loop();
#endif