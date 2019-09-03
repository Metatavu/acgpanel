@file:Suppress("DEPRECATION")

package fi.metatavu.acgpanel.cotio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegateImpl
import com.zeugmasolutions.localehelper.LocaleHelperApplicationDelegate
import fi.metatavu.acgpanel.support.pucomm.PeripheralUnitCommunicator
import fi.metatavu.acgpanel.support.ui.AppDrawerActivity
import fi.metatavu.acgpanel.support.ui.LockedDownActivity
import fi.metatavu.acgpanel.support.ui.showEditDialog
import kotlinx.android.synthetic.main.activity_add_code.*
import kotlinx.android.synthetic.main.activity_fill.*
import kotlinx.android.synthetic.main.activity_open_lockers.*
import android.app.admin.DeviceAdminReceiver as AndroidDeviceAdminReceiver
import kotlinx.android.synthetic.main.activity_read_code.*
import java.util.*
import kotlin.concurrent.thread
import kotlinx.android.synthetic.main.activity_default.unlock_button
    as default_activity_unlock_button
import kotlinx.android.synthetic.main.activity_default.code_textbox
    as default_activity_code_textbox
import kotlinx.android.synthetic.main.activity_read_code.unlock_button
    as read_code_unlock_button
import kotlinx.android.synthetic.main.activity_finished.unlock_button
    as finished_activity_unlock_button

class DeviceAdminReceiver: AndroidDeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        fun getComponentName(context: Context) =
            ComponentName(context.applicationContext, DeviceAdminReceiver::class.java)
    }

}

class CotioApplication: Application() {

    private val lockerClosedListeners = mutableListOf<() -> Unit>()
    private val localeAppDelegate = LocaleHelperApplicationDelegate()

    lateinit var model: CotioModel
    private lateinit var puComm: PeripheralUnitCommunicator
    private var lockOpen = false
    private var wakeLock: PowerManager.WakeLock? = null

    fun addLockerClosedListener(listener: () -> Unit) {
        lockerClosedListeners.add(listener)
    }

    fun removeLockerClosedListener(listener: () -> Unit) {
        lockerClosedListeners.remove(listener)
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
            Log.e(javaClass.name, "Couldn't grant auto permission: $e: " +
                Log.getStackTraceString(e))
            return false
        }
    }

    private val usbManager: UsbManager
        get() = getSystemService(Context.USB_SERVICE) as UsbManager

    private val powerManager: PowerManager
        get() = getSystemService(Context.POWER_SERVICE) as PowerManager

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        cosuLockDown()
        model = CotioModel(applicationContext)
        if (wakeLock == null) {
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "fi.metatavu.acgpanel.cotio:wakeLock")
            wakeLock!!.acquire()
        }
        val deviceList = usbManager.deviceList.values
        val device = deviceList.firstOrNull { it.vendorId == DEVICE_VENDOR_ID }
        grantAutomaticPermission(device ?: return)
        puComm = PeripheralUnitCommunicator(
            {},
            {
                lockOpen = false
                lockerClosedListeners.forEach {it()}
            },
            {},
            {!lockOpen})
        puComm.start(device, usbManager.openDevice(device) ?: return)
        // not guaranteed to run, but better than nothing
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            puComm.stop()
        })
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

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localeAppDelegate.attachBaseContext(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        localeAppDelegate.onConfigurationChanged(this)
    }

    companion object {
        private const val DEVICE_VENDOR_ID = 0x0403 // FTDI
    }

}

abstract class CotioActivity(lockAtStart: Boolean = true) : LockedDownActivity(numTaps = 3, lockAtStart = lockAtStart) {
    private val localeDelegate = LocaleHelperActivityDelegateImpl()

    private val rootView: View
        get() = findViewById(android.R.id.content)!!

    protected val model: CotioModel
        get() = cotioApplication.model

    protected val cotioApplication: CotioApplication
        get() = application as CotioApplication

    override val maintenancePasscode: String
        get() = model.maintenancePasscode

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeDelegate.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localeDelegate.onCreate(this)
        rootView.systemUiVisibility =
            SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR and
            FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
    }

    override fun onResume() {
        super.onResume()
        localeDelegate.onResumed(this)
    }

    override fun onPause() {
        super.onPause()
        localeDelegate.onPaused()
    }

    open fun updateLocale(locale: Locale) {
        localeDelegate.setLocale(this, locale)
    }
}

class DefaultActivity : CotioActivity() {

    override val unlockButton: View
        get() = default_activity_unlock_button

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_default)
        super.onCreate(savedInstanceState)
        default_activity_code_textbox.setOnKeyListener { _, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP &&
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                readCode(code_textbox.text.toString())
                code_textbox.text.clear()
            }
            false
        }
    }

    override fun afterUnlock() {
        super.afterUnlock()
        val intent = Intent(this, AppDrawerActivity::class.java)
        startActivity(intent)
    }

    fun proceedInFinnish(@Suppress("UNUSED_PARAMETER") view: View) {
        updateLocale(Locale.forLanguageTag("fi"))
        proceed()
    }

    fun proceedInEnglish(@Suppress("UNUSED_PARAMETER") view: View) {
        updateLocale(Locale.ENGLISH)
        proceed()
    }

    private fun proceed() {
        val intent = Intent(this, ReadCodeActivity::class.java)
        startActivity(intent)
    }

    private fun readCode(code: String) {
        val intent = Intent(this, ReadCodeActivity::class.java)
        intent.putExtra(ReadCodeActivity.EXTRA_CODE, code)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        default_activity_code_textbox.requestFocus()
        val view = currentFocus ?: View(this)
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

}

class ReadCodeActivity : CotioActivity() {

    private lateinit var handler: Handler

    private val lockClosedListener = {
        startActivity(Intent(this, FinishedActivity::class.java))
    }

    override val unlockButton: View
        get() = read_code_unlock_button

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    fun flashMessage(msg: String) {
        code_prompt.text = msg
        code_prompt.setTextColor(getColor(R.color.colorError))
        handler.postDelayed({
            if (intent.hasExtra(EXTRA_CODE)) {
                finish()
            } else {
                code_prompt.text = getString(R.string.read_code_prompt)
                code_prompt.setTextColor(getColor(R.color.colorPrimaryText))
            }
        }, 5000)
    }

    fun showCodeDialog(@Suppress("UNUSED_PARAMETER") view: View) {
        showEditDialog(getString(R.string.input_code_title), {
            code_textbox.requestFocus()
            readCode(it)
        })
    }

    fun goBack(@Suppress("UNUSED_PARAMETER") view: View) {
        finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        handler = Handler(mainLooper)
        setContentView(R.layout.activity_read_code)
        super.onCreate(savedInstanceState)
        code_textbox.setOnTouchListener { _, _ -> true }
        code_textbox.setOnKeyListener { view, i, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP &&
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                readCode(code_textbox.text.toString())
            }
            false
        }
    }

    private fun readCode(code: String) {
        thread(start = true) {
            val result = model.readCode(code)
            runOnUiThread {
                code_textbox.text.clear()
                when (result) {
                    CodeReadResult.CheckIn -> {
                        promptCloseDoor()
                    }
                    CodeReadResult.CheckOut -> {
                        promptCloseDoor()
                    }
                    CodeReadResult.CodeUsed -> {
                        flashMessage(getString(R.string.code_used_message))
                    }
                    CodeReadResult.NotFound -> {
                        flashMessage(getString(R.string.invalid_code_message))
                    }
                    CodeReadResult.NoFreeLockers -> {
                        flashMessage(getString(R.string.no_free_lockers_message))
                    }
                    CodeReadResult.TooFrequentReads -> {
                        // do nothing
                    }
                }
            }
        }
    }

    private fun promptCloseDoor() {
        code_prompt.text = getString(R.string.close_door_prompt)
        arrow.animation?.cancel()
        arrow.visibility = View.INVISIBLE
        show_code_button.isEnabled = false
        go_back_button.isEnabled = false
    }

    override fun onPause() {
        super.onPause()
        cotioApplication.removeLockerClosedListener(lockClosedListener)
    }

    override fun onResume() {
        super.onResume()
        cotioApplication.addLockerClosedListener(lockClosedListener)
        code_textbox.requestFocus()
        val view = currentFocus ?: View(this)
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onStart() {
        super.onStart()

        AnimationUtils.loadAnimation(this, R.anim.slide_up).also {
            hand.startAnimation(it)
        }

        Handler(mainLooper).postDelayed({
            AnimationUtils.loadAnimation(this, R.anim.bounce).also {
                arrow.startAnimation(it)
            }
        }, 2000)

        if (intent.hasExtra(EXTRA_CODE)) {
            readCode(intent.getStringExtra(EXTRA_CODE))
        }
    }

    companion object {
        val EXTRA_CODE = "fi.metatavu.acgpanel.CODE"
    }

}

class FinishedActivity : CotioActivity() {

    override val unlockButton: View
        get() = finished_activity_unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_finished)
        super.onCreate(savedInstanceState)
        Handler(mainLooper).postDelayed({finishAffinity()}, 5_000)
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        finishAffinity()
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

class AddCodeActivity : Activity() {

    private val model: CotioModel
        get() = (application as CotioApplication).model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_code)
        add_code_input.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    && event.action == KeyEvent.ACTION_UP) {
                addCode(null)
                true
            } else {
                false
            }
        }
    }

    fun addCode(@Suppress("UNUSED_PARAMETER") view: View?) {
        thread(start = true) {
            val result = model.addCode(add_code_input.text.toString())
            runOnUiThread {
                when (result) {
                    is CodeAddResult.Success -> {
                        info_text.text = getString(R.string.code_added)
                    }
                    is CodeAddResult.InvalidCode -> {
                        info_text.text = getString(R.string.code_add_error, result.error)
                    }
                }
                add_code_input.text.clear()
            }
        }
    }

    fun goBack(@Suppress("UNUSED_PARAMETER") view: View) {
        finishAffinity()
    }

}

class FillActivity : Activity() {

    private val onLockClosed = {
        fill_locker_info_text.text = getString(R.string.info_text_fill)
    }

    private val model: CotioModel
        get() = (application as CotioApplication).model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fill)
        fill_locker_input.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER
                && event.action == KeyEvent.ACTION_UP) {
                fillLocker(null)
                true
            } else {
                false
            }
        }
    }

    fun fillLocker(@Suppress("UNUSED_PARAMETER") view: View?) {
        thread(start = true) {
            val code = fill_locker_input.text.toString()
            val result = model.addCode(code)
            when (result) {
                is CodeAddResult.Success -> {
                    val result = model.readCode(code)
                    Log.d(javaClass.name, result.toString())
                    runOnUiThread {
                        fill_locker_info_text.text = getString(R.string.locker_filled)
                        fill_locker_input.text.clear()
                    }
                }
                is CodeAddResult.InvalidCode -> {
                    runOnUiThread {
                        fill_locker_info_text.text = getString(R.string.locker_fill_error, result.error)
                        fill_locker_input.text.clear()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val cotioApplication = application as CotioApplication
        cotioApplication.addLockerClosedListener(onLockClosed)
    }

    override fun onStop() {
        val cotioApplication = application as CotioApplication
        cotioApplication.removeLockerClosedListener(onLockClosed)
        super.onStop()
    }

    fun goBack(@Suppress("UNUSED_PARAMETER") view: View) {
        finishAffinity()
    }

}

private data class OpenLockerButtonModel(
    val driver: Int,
    val compartment: Int,
    val onClick: () -> Unit
)

private fun openLockerButtonView(context: Context): View {
    val dp = Resources.getSystem().displayMetrics.density
    val result = Button(context)
    result.layoutParams = RecyclerView.LayoutParams(
        (200*dp).toInt(),
        (100*dp).toInt()
    )
    result.textSize = 30*dp
    return result
}

private class OpenLockerButtonViewHolder(val context: Context):
        RecyclerView.ViewHolder(openLockerButtonView(context)) {

    @SuppressLint("SetTextI18n")
    fun populate(model: OpenLockerButtonModel) {
        with (itemView as Button) {
            text = "${model.driver}:${model.compartment}"
            setOnClickListener {
                model.onClick()
            }
        }
    }

}

private class OpenLockerButtonCallback: DiffUtil.ItemCallback<OpenLockerButtonModel>() {
    override fun areItemsTheSame(a: OpenLockerButtonModel, b: OpenLockerButtonModel): Boolean =
        a == b

    override fun areContentsTheSame(a: OpenLockerButtonModel, b: OpenLockerButtonModel): Boolean =
        a == b
}

class OpenLockersActivity: Activity() {

    private val model: CotioModel
        get() = (application as CotioApplication).model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_lockers)
        val adapter = object:
            ListAdapter<OpenLockerButtonModel, OpenLockerButtonViewHolder>(
                    OpenLockerButtonCallback()) {
            override fun onCreateViewHolder(parent: ViewGroup, index: Int): OpenLockerButtonViewHolder {
                return OpenLockerButtonViewHolder(parent.context)
            }
            override fun onBindViewHolder(holder: OpenLockerButtonViewHolder, index: Int) {
                holder.populate(getItem(index))
            }
        }
        open_lockers_buttons_grid.adapter = adapter
        val lockerButtonModels = model.enabledLockers
            .sortedBy {it.compartment}
            .sortedBy {it.driver}
            .map { locker ->
                OpenLockerButtonModel(locker.driver, locker.compartment) {
                    if (open_lockers_remove_reservation.isChecked) {
                        thread(start = true) {
                            model.clearLocker(locker.driver, locker.compartment)
                        }
                    }
                    model.openLocker(locker.driver, locker.compartment)
                    open_lockers_remove_reservation.isChecked = false
                }
            }
        adapter.submitList(lockerButtonModels)
    }

    fun goBack(@Suppress("UNUSED_PARAMETER") view: View) {
        finishAffinity()
    }

}
