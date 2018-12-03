package fi.metatavu.acgpanel

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import fi.metatavu.acgpanel.device.McuCommunicationService
import kotlinx.android.synthetic.main.activity_default.*

class DefaultActivity : PanelActivity(lockOnStart = false) {

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            Log.d(javaClass.name, "Receiver called")
            if (intent.action != ACTION_USB_PERMISSION) {
                return
            }
            val permissionGranted =
                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (permissionGranted) {
                Log.d(javaClass.name, "Permission granted")
                Handler(mainLooper).post {
                    startLockTask()
                }
            } else {
                Log.d(javaClass.name, "Permission denied")
                askForMcuPermission()
            }
        }
    }

    private val usbManager: UsbManager
        get() = getSystemService(Context.USB_SERVICE) as UsbManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default)
        registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
    }

    override fun onStart() {
        super.onStart()
        val mcuCommServiceIntent = Intent(this, McuCommunicationService::class.java)
        startService(mcuCommServiceIntent)
        val serverSyncServiceIntent = Intent(this, ServerSyncService::class.java)
        startService(serverSyncServiceIntent)
        askForMcuPermission()
   }

    private fun askForMcuPermission() {
        val deviceList = usbManager.deviceList.values
        val device = deviceList.firstOrNull { it.vendorId == CH340G_VENDOR_ID }
        if (device != null && !usbManager.hasPermission(device)) {
            usbManager.requestPermission(
                device,
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
            )
        } else {
            startLockTask()
        }
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, IdentifyActivity::class.java)
        startActivity(intent)
    }

    override val unlockButton: View
        get() = unlock_button

    companion object {
        private const val CH340G_VENDOR_ID = 0x1A86
        private const val ACTION_USB_PERMISSION = "fi.metatavu.acgpanel.USB_PERMISSION"
    }
}
