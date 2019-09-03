import serial
import serial.tools.list_ports
import msvcrt

def checksum(msg):
    full = sum(msg) % 256
    p1, p2 = divmod(full, 16)
    if p1 > 0x9:
        p1 += 7
    if p2 > 0x9:
        p2 += 7
    return bytes((p1 + 0x30, p2 + 0x30))

if __name__ == '__main__':
    port = None
    while port == None:
        for val in serial.tools.list_ports.comports():
            port, desc, info = val

    with serial.Serial(port, 9600, timeout=0.1) as s:
        print("ready")
        while True:
            i = s.read(100)
            if i:
                print(i)
            if msvcrt.kbhit():
                key = msvcrt.getch()
                if key == b'q':
                    break
                elif key == b'x':
                    s.write(b'\x00\x00')
                    print(s.read(100))
                elif key == b'p':
                    s.write(b'\x01\x02ID001\r')
                elif key == b'r':
                    s.write(b'\x01\x0201RES\r')
                    print(s.read(100))
                elif key == 'k':
                    s.write(b'\x02A00A1\r\n')
                    print(s.read(100))
                else:
                    s.write(b"\x01\x0201OPE00" + key + b"\r")
                    print(s.read(100))
