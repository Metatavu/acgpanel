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
import android.hardware.usb.UsbDevice
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.view.KeyEvent
import java.time.Duration

class DefaultActivity : PanelActivity(lockOnStart = false) {

    private val rebootTickCounter = TimedTickCounter(3, Duration.ofSeconds(1)) {
        powerManager.reboot("")
    }

    private val loginListener = {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        startActivity(intent)
    }

    private val failedLoginListener = {
        UnauthorizedDialog(this).show()
    }

    private val powerManager: PowerManager
        get() = getSystemService(Context.POWER_SERVICE) as PowerManager

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
                Handler(mainLooper).postDelayed({
                    startLockTask()
                }, PERMISSION_GRANT_WAIT_PERIOD)
            } else {
                Log.d(javaClass.name, "Permission denied")
                ensureMcuPermission()
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            rebootTickCounter.tick()
        }
        return super.onKeyUp(keyCode, event)
    }

    @SuppressLint("PrivateApi")
    private fun grantAutomaticPermission(usbDevice: UsbDevice): Boolean {
        try {
            // Use IUsbManager to grant access to device without asking user for permission
            // This only works if the app is installed in /system/priv-app/
            val context = this
            val pkgManager = context.packageManager
            val appInfo = pkgManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
            getServiceMethod.isAccessible = true
            val binder = getServiceMethod.invoke(null, Context.USB_SERVICE) as android.os.IBinder

            val iUsbManagerClass = Class.forName("android.hardware.usb.IUsbManager")
            val stubClass = Class.forName("android.hardware.usb.IUsbManager\$Stub")
            val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java)
            asInterfaceMethod.isAccessible = true
            val iUsbManager = asInterfaceMethod.invoke(null, binder)

            val grantDevicePermissionMethod = iUsbManagerClass.getDeclaredMethod(
                "grantDevicePermission",
                UsbDevice::class.java,
                Int::class.javaPrimitiveType
            )
            grantDevicePermissionMethod.isAccessible = true
            grantDevicePermissionMethod.invoke(iUsbManager, usbDevice, appInfo.uid)

            return true
        } catch (e: Exception) {
            // Not a privileged app, fall back to asking permission
            return false
        }

    }

    private val usbManager: UsbManager
        get() = getSystemService(Context.USB_SERVICE) as UsbManager

    private val activityManager: ActivityManager
        get() = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    @SuppressLint("WakelockTimeout")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default)
        version_number.text = packageManager.getPackageInfo(packageName, 0)!!.versionName
        registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        val mcuCommServiceIntent = Intent(this, McuCommunicationService::class.java)
        startService(mcuCommServiceIntent)
        val serverSyncServiceIntent = Intent(this, ServerSyncService::class.java)
        startService(serverSyncServiceIntent)
        if (wakeLock == null) {
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK,
                "fi.metatavu.acgpanel:wakeLock")
            wakeLock!!.acquire()
        }
    }

    override fun onDestroy() {
        model.removeLogInListener(loginListener)
        model.removeFailedLogInListener(failedLoginListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setupLogin()
    }

    override fun onStart() {
        super.onStart()
        ensureMcuPermission()
        setupLogin()
    }

    override fun onResume() {
        super.onResume()
        setupLogin()
        killOthers()
    }

    private fun setupLogin() {
        model.logOut()
        model.canLogInViaRfid = true
        // allow instant login for better usability
        model.removeLogInListener(loginListener)
        model.addLogInListener(loginListener)
        model.removeFailedLogInListener(failedLoginListener)
        model.addFailedLogInListener(failedLoginListener)
    }

    private fun killOthers() {
        val packages = packageManager.getInstalledApplications(0)
        for (p in packages) {
            if (!p.packageName.contains("fdroid")) {
                activityManager.killBackgroundProcesses(p.packageName)
            }
        }
    }

    override fun onPause() {
        model.removeLogInListener(loginListener)
        model.removeFailedLogInListener(failedLoginListener)
        model.canLogInViaRfid = false
        super.onPause()
    }

    private fun ensureMcuPermission() {
        val deviceList = usbManager.deviceList.values
        val device = deviceList.firstOrNull { it.vendorId == DEVICE_VENDOR_ID }
        if (device != null && !usbManager.hasPermission(device)) {
            if (!grantAutomaticPermission(device)) {
                usbManager.requestPermission(
                    device,
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
                )
            } else {
                startLockTask()
            }
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
        private const val DEVICE_VENDOR_ID = 0x0403 // FTDI
        private const val ACTION_USB_PERMISSION = "fi.metatavu.acgpanel.USB_PERMISSION"
        private const val PERMISSION_GRANT_WAIT_PERIOD = 500L
        private var wakeLock: PowerManager.WakeLock? = null
    }
}
