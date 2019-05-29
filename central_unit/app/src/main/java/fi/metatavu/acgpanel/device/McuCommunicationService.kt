package fi.metatavu.acgpanel.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import fi.metatavu.acgpanel.R
import fi.metatavu.acgpanel.model.*
import java.lang.Exception
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

sealed class Message

object Ping : Message()
object Pong : Message()
data class OpenLock(val shelf: Int, val compartment: Int): Message()
data class OpenLockConfirmation(val shelf: Int): Message()
data class ReadCard(val cardId: String): Message()
data class LockClosed(val shelf: Int): Message()
data class ResetLock(val shelf: Int): Message()
data class ResetLockConfirmation(val shelf: Int): Message()
data class AssignShelf(val shelf: Int): Message()
data class SetLightsIntensity(val intensity: Int): Message()
object AssignShelfConfirmation : Message()

private class NoResponseException : Exception("No response from device")

private const val ZERO = '0'.toByte()
private const val RESTART_INTERVAL_MS = 1000L
private const val RESTART_MAX_TRIES = 10
private const val READ_TIMEOUT_MS = 5000L
private const val READ_TIMEOUT_MAX_FAILURES = 5
private const val PING_INTERVAL_MS = 15L*1000L
private const val MAX_BAD_PINGS = 2

abstract class MessageReader {

    abstract fun read(): Byte
    abstract fun available(): Boolean

    private var lastByte = 0.toByte()

    private fun next(): Byte {
        lastByte = read()
        return lastByte
    }

    private fun chomp() {
        Thread.sleep(10)
        while (available()) {
            read()
        }
    }

    fun readMessage(): Message? {
        try {
            when (next()) {
                0x00.toByte() -> {
                    while (available()) {
                        read()
                    }
                    return Pong
                }
                0x01.toByte() -> {
                    while (next() != 0x02.toByte()) {
                        if (!available()) {
                            return null
                        }
                    }
                    if (next() == 'O'.toByte()) {
                        if (next() == 'K'.toByte()) {
                            chomp()
                            return AssignShelfConfirmation
                        }
                        chomp()
                        return null
                    } else {
                        val shelf = (lastByte - ZERO) * 10 + (next() - ZERO)
                        val code = String(byteArrayOf(next(), next(), next()), StandardCharsets.US_ASCII)
                        chomp()
                        return when (code) {
                            "OKO" -> OpenLockConfirmation(shelf)
                            "RE\r" -> LockClosed(shelf)
                            "RS\r" -> ResetLockConfirmation(shelf)
                            else -> null
                        }
                    }
                }
                0x02.toByte() -> {
                    next() // skip first 'B'
                    val bytes = mutableListOf<Byte>()
                    while (next() != '='.toByte()) {
                        bytes.add(lastByte)
                    }
                    chomp()
                    return ReadCard(String(bytes.toByteArray(), StandardCharsets.US_ASCII))
                }
                0x03.toByte() -> {
                    val bytes = mutableListOf<Byte>()
                    while (available()) {
                        bytes.add(next())
                    }
                    Thread.sleep(60) // the last character arrives after slight delay
                    while (available()) {
                        bytes.add(next())
                    }
                    return ReadCard(String(bytes.toByteArray(), StandardCharsets.US_ASCII))
                }
                else -> {
                    while (available()) {
                        read()
                    }
                    return null
                }
            }
        } catch (e: TimeoutException) {
            return null
        }
    }
}

abstract class MessageWriter {

    abstract fun write(byte: Byte)
    abstract fun flush()

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
            is AssignShelf -> {
                write(0x01)
                write(0x02)
                write('I'.toByte())
                write('D'.toByte())
                write(ZERO)
                write(((msg.shelf / 10) + ZERO).toByte())
                write(((msg.shelf % 10) + ZERO).toByte())
                write(0x0D)
                flush()
            }
            is SetLightsIntensity -> {
                write(0x04)
                write(msg.intensity.toByte())
                flush()
            }
        }
    }

}

class DisconnectException : Exception("Device disconnected")

sealed class Action {
    data class LockOpen(
        val shelf: Int,
        val compartment: Int,
        val reset: Boolean
    ): Action()
    data class AssignShelf(
        val shelf: Int
    ): Action()
    data class SetLightsIntensity(
        val intensity: Int
    ): Action()
}

const val MCU_COMMUNICATION_SERVICE_ID = 1

class McuCommunicationService : Service() {
    private val usbManager: UsbManager
        get() = getSystemService(Context.USB_SERVICE) as UsbManager

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val inBuffer = ArrayBlockingQueue<Byte>(BUFFER_SIZE)
    private val outBuffer = ArrayBlockingQueue<Byte>(BUFFER_SIZE)
    private val lockModel = getLockModel()
    private val maintenanceModel = getMaintenanceModel()
    private val demoModel = getDemoModel()
    private val loginModel = getLoginModel()
    private val lightsModel = getLightsModel()
    private var jobThread: Thread? = null
    private var serial: UsbSerialDevice? = null
    private var readFailures = 0
    private val actions = ArrayBlockingQueue<Action>(BUFFER_SIZE)
    private val onLockOpenRequest: (LockOpenRequest) -> Unit = {
        actions.add(Action.LockOpen(it.shelf, it.compartment, it.reset))
    }
    private val onAssignShelfRequest: (AssignShelfRequest) -> Unit = {
        actions.add(Action.AssignShelf(it.shelf))
    }
    private val onLightsRequest: (LightsRequest) -> Unit = {
        actions.add(Action.SetLightsIntensity(it.intensity))
    }

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
                Log.d(javaClass.name, "Outgoing: " +
                        outBuffer.toByteArray().toString(StandardCharsets.ISO_8859_1)
                )
                outBuffer.clear()
            }
        }
    }

    private var lastPing = Instant.now()
    private var badPings = 0
    private var serviceRunning = false

    private fun process() {
        while (jobThread != null) {
            try {
                val action = actions.poll()
                when (action) {
                    is Action.LockOpen -> {
                        if (action.reset) {
                            messageWriter.writeMessage(ResetLock(action.shelf))
                            val resetResp = messageReader.readMessage()
                            if (resetResp !is ResetLockConfirmation) {
                                Log.e(javaClass.name, "BoxDriver didn't respond to reset")
                            }
                            Thread.sleep(100)
                        }
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
                                Thread.sleep(50)
                                messageWriter.writeMessage(OpenLock(action.shelf, action.compartment))
                                messageReader.readMessage()
                                Log.e(javaClass.name, "Lock ${action.shelf}/${action.compartment} was open")
                            }
                        }
                    }
                    is Action.AssignShelf -> {
                        messageWriter.writeMessage(AssignShelf(action.shelf))
                        messageReader.readMessage() ?: throw NoResponseException()
                    }
                    is Action.SetLightsIntensity -> {
                        messageWriter.writeMessage(SetLightsIntensity(action.intensity))
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
                                loginModel.logIn(msg.cardId)
                            }
                        }
                        is LockClosed -> {
                            Handler(mainLooper).postDelayed({
                                lockModel.openLock(first = false)
                            }, 100)
                        }
                    }
                }
                if (!lockModel.locksOpen &&
                    !lockModel.isShelvingMode &&
                    !lockModel.isCalibrationMode &&
                    lastPing.isBefore(Instant.now().minusMillis(PING_INTERVAL_MS))) {
                    Log.i(javaClass.name, "Pinging device...")
                    lastPing = Instant.now()
                    messageWriter.writeMessage(Ping)
                    if (messageReader.readMessage() !is Pong) {
                        badPings++
                        if (badPings > MAX_BAD_PINGS) {
                            jobThread = null
                            serial!!.close()
                            return
                        }
                    } else {
                        badPings = 0
                    }
                }
                Thread.sleep(1)
            } catch (ex: TimeoutException) {
                Log.d(javaClass.name, "Timeout: $ex")
            } catch (ex: Exception) {
                Log.e(javaClass.name, "Lock/RFID module communication error: $ex")
                maintenanceModel.triggerDeviceError(ex.message ?: "")
                jobThread = null
                serial!!.close()
            }
        }
    }

    private fun tryStart(): Boolean {
        stop()
        val deviceList = usbManager.deviceList.values
        val device = deviceList.firstOrNull { it.vendorId == DEVICE_VENDOR_ID }
        if (device != null) {
            if (!usbManager.hasPermission(device)) {
                return false
            }
            val connection = usbManager.openDevice(device) ?: return false
            serial = UsbSerialDevice.createUsbSerialDevice(device, connection)
            serial!!.open()
            serial!!.read {
                inBuffer.addAll(it.asIterable())
                Log.d(javaClass.name, "Incoming: " +
                        it.toString(StandardCharsets.ISO_8859_1))
            }
            jobThread = thread(start = true) { process() }
            return true
        } else {
            return false
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
        return Service.START_STICKY
    }

    override fun onCreate() {
        val handler = Handler()
        lateinit var autorestarter: Runnable
        var numFailures = 0
        autorestarter = Runnable {
            if (jobThread == null) {
                Log.i(javaClass.name, "Trying to restart")
                val success = tryStart()
                if (success) {
                    Log.i(javaClass.name, "Restart succeeded")
                    numFailures = 0
                } else {
                    Log.i(javaClass.name, "Restart failed, $numFailures/$RESTART_MAX_TRIES")
                    numFailures++
                    if (numFailures > RESTART_MAX_TRIES && !demoModel.demoMode) {
                        maintenanceModel.triggerDeviceError("Couldn't connect to device")
                    }
                }
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
            .setSmallIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setLargeIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .build()
        startForeground(MCU_COMMUNICATION_SERVICE_ID, notification)
        lockModel.addLockOpenRequestListener(onLockOpenRequest)
        lockModel.addAssignShelfRequestListener(onAssignShelfRequest)
        lightsModel.addLightsRequestListener(onLightsRequest)
    }

    override fun onDestroy() {
        stop()
        lockModel.removeLockOpenRequestListener(onLockOpenRequest)
        lockModel.removeAssignShelfRequestListener(onAssignShelfRequest)
        lightsModel.removeLightsRequestListener(onLightsRequest)
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
