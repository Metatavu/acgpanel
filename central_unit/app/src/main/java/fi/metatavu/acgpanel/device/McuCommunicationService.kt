package fi.metatavu.acgpanel.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import fi.metatavu.acgpanel.R
import fi.metatavu.acgpanel.model.OpenLockAction
import fi.metatavu.acgpanel.model.PanelModelImpl
import java.lang.Exception
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

sealed class Message {
    abstract val number: Int
}

data class Acknowledgement(override val number: Int, val target: Int): Message()
data class OpenLock(override val number: Int, val shelf: Int, val compartment: Int): Message()
data class LockStateRequest(override val number: Int, val shelf: Int, val compartment: Int): Message()
data class LockStateReply(override val number: Int, val shelf: Int, val compartment: Int, val open: Boolean): Message()
data class ReadCard(override val number: Int, val cardId: String): Message()
data class LockClosed(override val number: Int, val shelf: Int, val compartment: Int): Message()

private class InvalidMessageException : Exception()

private fun bytes2string(bytes: Collection<Byte>): String {
    return String(bytes.toByteArray(), Charsets.US_ASCII)
}

private const val ZERO = '0'.toByte()
private const val NINE = '9'.toByte()
private const val START_OF_MESSAGE = 0x02.toByte()
private const val SEPARATOR = ';'.toByte()
private const val END_OF_MESSAGE = '\n'.toByte()
private const val RESTART_INTERVAL_MS = 1000L
private const val READ_TIMEOUT_MS = 1000L
private const val READ_TIMEOUT_MAX_FAILURES = 5

abstract class MessageReader {

    abstract fun read(): Byte
    abstract fun available(): Boolean

    private var checksum = 0
    private var lastByte = 0.toByte()

    private fun next(allowCc: Boolean = false, computeChecksum: Boolean = true): Byte {
        lastByte = read()
        if (computeChecksum) {
            checksum = checksum xor lastByte.toInt()
        }
        if (!allowCc && lastByte < 0x20) {
            throw InvalidMessageException()
        }
        return lastByte
    }

    private fun readInt(computeChecksum: Boolean = true): Int {
        var result = 0
        while (next(computeChecksum = computeChecksum) in ZERO..NINE) {
            result *= 10
            result += lastByte - ZERO
        }
        return result
    }

    fun readMessage(): Message? {
        checksum = 0
        val payload = mutableListOf<Byte>()
        try {
            if (!available() or (next(allowCc = true) != START_OF_MESSAGE)) {
                return null
            }
            val messageType = readInt()
            if (lastByte != SEPARATOR) {
                throw InvalidMessageException()
            }
            val messageNumber = readInt()
            if (lastByte != SEPARATOR) {
                throw InvalidMessageException()
            }
            val length = readInt()
            if (lastByte != SEPARATOR) {
                throw InvalidMessageException()
            }
            for (i in 0 until length) {
                payload.add(next())
            }
            if (next() != SEPARATOR) {
                throw InvalidMessageException()
            }
            val msgChecksum = readInt(computeChecksum = false)
            if (lastByte != SEPARATOR) {
                throw InvalidMessageException()
            }
            if (checksum != msgChecksum) {
                throw InvalidMessageException()
            }
            if (next(allowCc = true) != END_OF_MESSAGE) {
                throw InvalidMessageException()
            }
            try {
                return when (messageType) {
                    0 -> Acknowledgement(
                        messageNumber,
                        bytes2string(payload).toInt()
                    )
                    1 -> OpenLock(
                        messageNumber,
                        bytes2string(payload).split(";")[0].toInt(),
                        bytes2string(payload).split(";")[1].toInt()
                    )
                    2 -> LockStateRequest(
                        messageNumber,
                        bytes2string(payload).split(";")[0].toInt(),
                        bytes2string(payload).split(";")[1].toInt()
                    )
                    3 -> LockStateReply(
                        messageNumber,
                        bytes2string(payload).split(";")[0].toInt(),
                        bytes2string(payload).split(";")[1].toInt(),
                        bytes2string(payload).split(";")[2] == "1"
                    )
                    4 -> ReadCard(
                        messageNumber,
                        bytes2string(payload)
                    )
                    5 -> LockClosed(
                        messageNumber,
                        bytes2string(payload).split(";")[0].toInt(),
                        bytes2string(payload).split(";")[1].toInt()
                    )
                    else -> null
                }
            } catch (ex: ArrayIndexOutOfBoundsException) {
                return null
            }
        } catch (ex: InvalidMessageException) {
            Log.e(javaClass.name, "$ex")
            return null
        }
    }

}

abstract class MessageWriter {

    abstract fun write(byte: Byte)
    abstract fun flush()

    private var checksum = 0

    private fun writeByte(byte: Byte, computeChecksum: Boolean = true) {
        if (computeChecksum) {
            checksum = checksum xor byte.toInt()
        }
        write(byte)
    }

    private fun writeString(str: String, computeChecksum: Boolean = true) {
        for (byte in str.toByteArray(Charsets.US_ASCII)) {
            writeByte(byte, computeChecksum)
        }
    }

    fun writeMessage(msg: Message) {
        checksum = 0
        when (msg) {
            is Acknowledgement -> {
                writeByte(START_OF_MESSAGE)
                writeString("0")
                writeByte(SEPARATOR)
                writeString(msg.number.toString())
                writeByte(SEPARATOR)
                writeString(msg.target.toString().length.toString())
                writeByte(SEPARATOR)
                writeString(msg.target.toString())
                writeByte(SEPARATOR)
                writeString(checksum.toString(), computeChecksum = false)
                writeByte(SEPARATOR)
                writeByte(END_OF_MESSAGE)
                flush()
            }
            is OpenLock -> {
                val payload = "${msg.shelf};${msg.compartment}"
                Log.d(javaClass.name, "Opening lock: $payload")
                writeByte(START_OF_MESSAGE)
                writeString("1")
                writeByte(SEPARATOR)
                writeString(msg.number.toString())
                writeByte(SEPARATOR)
                writeString(payload.length.toString())
                writeByte(SEPARATOR)
                writeString(payload)
                writeByte(SEPARATOR)
                writeString(checksum.toString(), computeChecksum = false)
                writeByte(SEPARATOR)
                writeByte(END_OF_MESSAGE)
                flush()
            }
        }
    }

}

class DisconnectException : Exception("Device disconnected")

const val MCU_COMMUNICATION_SERVICE_ID = 1

class McuCommunicationService : Service() {
    private val usbManager: UsbManager
        get() = getSystemService(Context.USB_SERVICE) as UsbManager

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val model = PanelModelImpl

    private var jobThread: Thread? = null
    private var serial: UsbSerialDevice? = null
    private val inBuffer = ArrayBlockingQueue<Byte>(BUFFER_SIZE)
    private val outBuffer = ArrayBlockingQueue<Byte>(BUFFER_SIZE)
    private var readFailures = 0

    private val messageReader = object : MessageReader() {
        override fun read(): Byte {
            val byte = inBuffer.poll(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (byte != null) {
                readFailures = 0
                return byte
            } else {
                readFailures++
                if (readFailures > READ_TIMEOUT_MAX_FAILURES) {
                    readFailures = 0
                    throw DisconnectException()
                }
                throw TimeoutException()
            }
        }
        override fun available(): Boolean {
            return inBuffer.isNotEmpty()
        }
    }

    private val messageWriter = object : MessageWriter() {
        override fun write(byte: Byte) {
            outBuffer.add(byte)
        }
        override fun flush() {
            val ser = serial
            if (ser != null) {
                ser.write(outBuffer.toByteArray())
                outBuffer.clear()
            }
        }
    }

    private fun process() {
        var sentMessageNumber = 0
        var recvMessageNumber = -1
        // TODO reliability for message sending
        while (jobThread != null) {
            try {
                val action = model.nextAction()
                when (action) {
                    is OpenLockAction -> {
                        for (i in 0..6) {
                            messageWriter.writeMessage(
                                OpenLock(sentMessageNumber,
                                         action.shelf,
                                         action.compartment)
                            )
                            Thread.sleep(100)
                        }
                        sentMessageNumber = (sentMessageNumber + 1) and 0x7FFF
                    }
                }

                val msg = messageReader.readMessage()
                if (msg != null) {
                    Log.d(javaClass.name, "Received message $msg")
                    // TODO wrap around
                    if (msg.number <= recvMessageNumber) {
                        continue
                    } else {
                        recvMessageNumber = msg.number
                    }
                }
                when (msg) {
                    is Acknowledgement -> {
                        Log.d(javaClass.name, "Got acknowledgement: $msg")
                    }
                    is ReadCard -> {
                        for (i in 0..3) {
                            messageWriter.writeMessage(
                                Acknowledgement(sentMessageNumber, msg.number)
                            )
                            Thread.sleep(100)
                        }
                        sentMessageNumber = (sentMessageNumber + 1) and 0x7FFF
                        Handler(mainLooper).post {
                            model.logIn(msg.cardId, usingRfid = true)
                        }
                    }
                    is LockClosed -> {
                        for (i in 0..3) {
                            messageWriter.writeMessage(
                                Acknowledgement(sentMessageNumber, msg.number)
                            )
                            Thread.sleep(100)
                        }
                        sentMessageNumber = (sentMessageNumber + 1) and 0x7FFF
                        Handler(mainLooper).post {
                            model.openLock(first = false)
                        }
                    }
                }
                Thread.sleep(50)
            } catch (ex: InvalidMessageException) {
                Log.d(javaClass.name, "Invalid message: $ex")
            } catch (ex: TimeoutException) {
                Log.d(javaClass.name, "Timeout: $ex")
            } catch (ex: Exception) {
                Log.e(javaClass.name, "Lock/RFID module communication error: $ex")
                jobThread = null
                serial!!.close()
            }
        }
    }

    private fun tryStart() {
        stop()
        val deviceList = usbManager.deviceList.values
        val device = deviceList.firstOrNull { it.vendorId == DEVICE_VENDOR_ID }
        if (device != null) {
            if (!usbManager.hasPermission(device)) {
                return
            }
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                return
            }
            serial = UsbSerialDevice.createUsbSerialDevice(device, connection)
            serial!!.open()
            serial!!.read {
                inBuffer.addAll(it.asIterable())
            }
            jobThread = thread(start = true) { process() }
        }
    }

    private fun stop() {
        val thread = jobThread
        if (thread != null) {
            jobThread = null
            thread.join()
        }
        if (serial != null) {
            serial!!.close()
            serial = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val handler = Handler()
        lateinit var autorestarter: Runnable
        autorestarter = Runnable {
            if (jobThread == null) {
                tryStart()
            }
            handler.postDelayed(autorestarter, RESTART_INTERVAL_MS)
        }
        autorestarter.run()
        val channel = NotificationChannel(
            getString(R.string.app_name),
            getString(R.string.notifications_name),
            NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, channel.id)
            .setContentTitle(getString(R.string.mcu_communication_title))
            .setContentText(getString(R.string.mcu_communication_desc))
            .build()
        startForeground(MCU_COMMUNICATION_SERVICE_ID, notification)
        return Service.START_STICKY
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val DEVICE_VENDOR_ID = 0x0403 // FTDI
        const val BUFFER_SIZE = 1024*1024
    }
}
