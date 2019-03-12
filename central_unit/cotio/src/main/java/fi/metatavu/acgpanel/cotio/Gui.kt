package fi.metatavu.acgpanel.cotio

import android.annotation.SuppressLint
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import fi.metatavu.acgpanel.support.pucomm.PeripheralUnitCommunicator
import fi.metatavu.acgpanel.support.ui.AppDrawerActivity
import fi.metatavu.acgpanel.support.ui.LockedDownActivity
import android.app.admin.DeviceAdminReceiver as AndroidDeviceAdminReceiver
import kotlinx.android.synthetic.main.activity_read_code.*
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlinx.android.synthetic.main.activity_default.unlock_button
    as default_activity_unlock_button
import kotlinx.android.synthetic.main.activity_read_code.unlock_button
    as read_code_unlock_button

class DeviceAdminReceiver: AndroidDeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        fun getComponentName(context: Context) =
            ComponentName(context.applicationContext, DeviceAdminReceiver::class.java)
    }

}

class CotioApplication: Application() {

    lateinit var model: CotioModel
    private lateinit var puComm: PeripheralUnitCommunicator
    private var lockOpen = false
    private var wakeLock: PowerManager.WakeLock? = null

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
            throw IllegalStateException("Couldn't grant permission", e)
        }
    }

    private val usbManager: UsbManager
        get() = getSystemService(Context.USB_SERVICE) as UsbManager

    private val powerManager: PowerManager
        get() = getSystemService(Context.POWER_SERVICE) as PowerManager

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        model = CotioModel(applicationContext)
        if (wakeLock == null) {
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "fi.metatavu.acgpanel.cotio:wakeLock")
            wakeLock!!.acquire()
        }
        val deviceList = usbManager.deviceList.values
        val device = deviceList.firstOrNull { it.vendorId == DEVICE_VENDOR_ID }
        grantAutomaticPermission(device ?: return)
        cosuLockDown()
        puComm = PeripheralUnitCommunicator(
            {},
            {lockOpen = false},
            {},
            {!lockOpen})
        puComm.start(device, usbManager.openDevice(device)
            ?: throw IllegalStateException("Couldn't open device"))
        thread(start = true) {
            model.init()
            model.addLockOpenRequestListener {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this,
                        "Lukko ${it.driver}/${it.compartment} avattu",
                        Toast.LENGTH_LONG
                    ).show()
                }
                puComm.openLock(it.driver, it.compartment, false)
                lockOpen = true
            }
        }
    }

    private fun cosuLockDown() {
        val adminComponentName = DeviceAdminReceiver.getComponentName(this)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(packageName)) {
            return
        }
        dpm.addUserRestriction(adminComponentName, UserManager.DISALLOW_SAFE_BOOT)
        dpm.clearUserRestriction(adminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        dpm.setKeyguardDisabled(adminComponentName, true)
        dpm.setStatusBarDisabled(adminComponentName, true)
        dpm.setSystemUpdatePolicy(adminComponentName,
            SystemUpdatePolicy.createWindowedInstallPolicy(60, 120))
        dpm.setLockTaskPackages(adminComponentName, arrayOf(packageName))
        val intentFilter = IntentFilter(Intent.ACTION_MAIN)
        intentFilter.addCategory(Intent.CATEGORY_HOME)
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT)
        dpm.addPersistentPreferredActivity(
            adminComponentName,
            intentFilter,
            ComponentName(packageName, DefaultActivity::class.java.name)
        )
    }

    override fun onTerminate() {
        puComm.stop()
        super.onTerminate()
    }

    companion object {
        private const val DEVICE_VENDOR_ID = 0x0403 // FTDI
    }

}

class DefaultActivity : LockedDownActivity() {

    override val maintenancePasscode: String
        get() = "0000"

    override val unlockButton: View
        get() = default_activity_unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_default)
        super.onCreate(savedInstanceState)
    }

    override fun afterUnlock() {
        super.afterUnlock()
        val intent = Intent(this, AppDrawerActivity::class.java)
        startActivity(intent)
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ReadCodeActivity::class.java)
        startActivity(intent)
    }

}

class ReadCodeActivity : LockedDownActivity() {

    private lateinit var model: CotioModel

    override val maintenancePasscode: String
        get() = "0000"

    override val unlockButton: View
        get() = read_code_unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        model = (application as CotioApplication).model
        setContentView(R.layout.activity_read_code)
        super.onCreate(savedInstanceState)
        code_textbox.setOnKeyListener { view, i, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP &&
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                thread(start = true) {
                    val result = model.readCode(code_textbox.text.toString())
                    runOnUiThread {
                        code_textbox.text.clear()
                        when (result) {
                            CodeReadResult.CheckIn -> {
                                code_prompt.text = "CheckIn"
                            }
                            CodeReadResult.CheckOut -> {
                                code_prompt.text = "CheckOut"
                            }
                            CodeReadResult.CodeUsed -> {
                                code_prompt.text = "CodeUsed"
                            }
                            CodeReadResult.NotFound -> {
                                code_prompt.text = "NotFound"
                            }
                        }
                    }
                }
                true
            }
            false
        }
    }

}

class CotioSettingsActivity: PreferenceActivity() {

    override fun onBuildHeaders(target: List<PreferenceActivity.Header>) {
        loadHeadersFromResource(R.xml.pref_headers, target)
    }

    override fun isValidFragment(fragmentName: String): Boolean {
        return PreferenceFragment::class.java.name == fragmentName
                || GeneralPreferenceFragment::class.java.name == fragmentName
    }

    class GeneralPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_general)
            setHasOptionsMenu(true)
        }
        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val id = item.itemId
            if (id == android.R.id.home) {
                startActivity(Intent(activity, CotioSettingsActivity::class.java))
                return true
            }
            return super.onOptionsItemSelected(item)
        }
    }

}

