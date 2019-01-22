import serial
import serial.tools.list_ports
import msvcrt

def checksum(s):
    res = 0
    for c in s:
        res = res ^ c
    return res


port = None
while port == None:
    for val in serial.tools.list_ports.comports():
        port, desc, info = val

with serial.Serial(port, 9600, timeout=0.1) as s:
    print("ready")
    while True:
        i = s.read(100)
        ib = bytes(x for x in i if x != 0)
        if ib:
            print(ib)
        if msvcrt.kbhit():
            key = msvcrt.getch()
            if key == b'q':
                break
            if key == b'x':
                s.write(b'\x02' + b'1;0;0;;51;\n')
                continue
            payload = b'1;' + key
            msgnocs = b'\02' + (f"1;0;{len(payload)};{payload.decode('ascii')};".encode('ascii'))
            cs = checksum(msgnocs)
            msg = f"{msgnocs.decode('ascii')}{cs};\n".encode('ascii')
            print(msg)
            s.write(msg)
            print(s.read(100))
