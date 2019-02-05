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
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

sealed class Message {
}

class Ping: Message() {
    override fun equals(other: Any?): Boolean {
        return this === other
    }
    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }
}
class Pong: Message() {
    override fun equals(other: Any?): Boolean {
        return this === other
    }
    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }
}
data class OpenLock(val shelf: Int, val compartment: Int): Message()
data class OpenLockConfirmation(val shelf: Int): Message()
data class ReadCard(val cardId: String): Message()
data class LockClosed(val shelf: Int): Message()
data class ResetLock(val shelf: Int): Message()
data class ResetLockConfirmation(val shelf: Int): Message()

private class NoResponseException : Exception("No response from device")

private fun bytes2string(bytes: Collection<Byte>): String {
    return String(bytes.toByteArray(), Charsets.US_ASCII)
}

private const val ZERO = '0'.toByte()
private const val NINE = '9'.toByte()
private const val A = 'A'.toByte()
private const val Z = 'Z'.toByte()
private const val RESTART_INTERVAL_MS = 1000L
private const val READ_TIMEOUT_MS = 1000L
private const val READ_TIMEOUT_MAX_FAILURES = 5

abstract class MessageReader {

    abstract fun read(): Byte
    abstract fun available(): Boolean

    private var lastByte = 0.toByte()

    private fun next(): Byte {
        lastByte = read()
        return lastByte
    }

    fun readMessage(): Message? =
        when (next()) {
            0x00.toByte() -> {
                while (available()) {
                    read()
                }
                Pong()
            }
            0x01.toByte() -> {
                next() // STX
                val shelf = (next() - ZERO) * 10 + (next() - ZERO)
                val code = String(byteArrayOf(next(), next(), next()), StandardCharsets.US_ASCII)
                while (available()) {
                    read()
                }
                when (code) {
                    "OKO" -> OpenLockConfirmation(shelf)
                    "RE\r" -> LockClosed(shelf)
                    "RS\r" -> ResetLockConfirmation(shelf)
                    else -> null
                }
            }
            0x02.toByte() -> {
                next() // skip first 'B'
                val bytes = mutableListOf<Byte>()
                while (next() != '='.toByte()) {
                    bytes.add(lastByte)
                }
                while (available()) {
                    read()
                }
                ReadCard(String(bytes.toByteArray(), StandardCharsets.US_ASCII))
            }
            else -> null
        }
    }

abstract class MessageWriter {

    abstract fun write(byte: Byte)
    abstract fun flush()

    private fun writeString(str: String) {
        for (byte in str.toByteArray(Charsets.US_ASCII)) {
            write(byte)
        }
    }

    fun writeMessage(msg: Message) {
        when (msg) {
            is Ping -> {
                write(0x00)
                write(0x00)
                flush()
            }
            is OpenLock -> {
                write(0x01)
                write(0x02)
                write(((msg.shelf / 10) + ZERO).toByte())
                write(((msg.shelf % 10) + ZERO).toByte())
                write('O'.toByte())
                write('P'.toByte())
                write('E'.toByte())
                write(ZERO)
                write(((msg.compartment / 10) + ZERO).toByte())
                write(((msg.compartment % 10) + ZERO).toByte())
                write(0x0D)
                flush()
            }
            is ResetLock -> {
                write(0x01)
                write(0x02)
                write(((msg.shelf / 10) + ZERO).toByte())
                write(((msg.shelf % 10) + ZERO).toByte())
                write('R'.toByte())
                write('E'.toByte())
                write('S'.toByte())
                write(0x0D)
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
                Log.d(javaClass.name, "Outgoing: ${outBuffer.map{ it.toUByte().toString(16)}.joinToString()}")
                outBuffer.clear()
            }
        }
    }

    private var pingCounter = 0

    private fun process() {
        while (jobThread != null) {
            try {
                val action = model.nextAction()
                when (action) {
                    is OpenLockAction -> {
                        messageWriter.writeMessage(OpenLock(action.shelf, action.compartment))
                        var resp = messageReader.readMessage()
                        if (resp !is OpenLockConfirmation) {
                            messageWriter.writeMessage(OpenLock(action.shelf, action.compartment))
                            resp = messageReader.readMessage()
                            if (resp !is OpenLockConfirmation) {
                                messageWriter.writeMessage(ResetLock(action.shelf))
                                resp = messageReader.readMessage()
                                if (resp !is ResetLockConfirmation) {
                                    throw NoResponseException()
                                }
                            }
                        }
                    }
                }
                if (messageReader.available()) {
                    val msg = messageReader.readMessage()
                    if (msg != null) {
                        Log.e(javaClass.name, "Received message $msg")
                    }
                    when (msg) {
                        is ReadCard -> {
                            Handler(mainLooper).post {
                                model.logIn(msg.cardId, usingRfid = true)
                            }
                        }
                        is LockClosed -> {
                            Handler(mainLooper).post {
                                model.openLock(first = false)
                            }
                        }
                    }
                } else {
                    if (pingCounter == 0) {
                        messageWriter.writeMessage(Ping())
                        messageReader.readMessage()
                        pingCounter = 1000
                    }
                    Thread.sleep(50)
                }
            } catch (ex: TimeoutException) {
                Log.d(javaClass.name, "Timeout: $ex")
            } catch (ex: Exception) {
                Log.e(javaClass.name, "Lock/RFID module communication error: $ex")
                model.triggerDeviceError(ex.message ?: "")
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
                Log.d(javaClass.name, "Incoming: ${inBuffer.map{ it.toString(16)}.joinToString()}")
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
