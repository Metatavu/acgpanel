package fi.metatavu.acgpanel

import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothClass
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.inputmethod.InputMethodManager
import eu.chainfire.libsuperuser.Shell
import fi.metatavu.acgpanel.model.PanelModelImpl

private const val ANDROID_LAUNCHER = "com.android.launcher3"

abstract class PanelActivity(private val lockOnStart: Boolean = false)
        : Activity() {

    protected val model = PanelModelImpl

    private var lastUnlockClickTime = Long.MIN_VALUE
    private var unlockClickCount = 0
    private val maxUnlockClickDelay = 1000
    private val unlockClicksRequired = 10
    private val onLogout = {
        if (this !is DefaultActivity) {
            finish()
        }
    }
    private val onDeviceError = { msg: String ->
        DeviceErrorDialog(this, msg, model).show()
    }

    private val activityManager: ActivityManager
        get() = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private val rootView: View
        get() = findViewById(android.R.id.content)!!

    override fun onStart() {
        super.onStart()
        isImmersive = true
        rootView.keepScreenOn = true
        rootView.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (lockOnStart) {
            val activityManager = activityManager
            @Suppress("DEPRECATION")
            if (!activityManager.isInLockTaskMode) {
                startLockTask()
            }
        }
        unlockButton.setOnClickListener { initiateUnlock(); }
        model.addLogOutListener(onLogout)
    }

    override fun onDestroy() {
        model.removeLogOutListener(onLogout)
        super.onDestroy()
    }

    override fun onResume() {
        model.addDeviceErrorListener(onDeviceError)
        super.onResume()
    }

    override fun onPause() {
        model.removeDeviceErrorListener(onDeviceError)
        super.onPause()
    }

    protected fun enableSoftKeyboard(view: View) {
        view.setOnFocusChangeListener { v, focused ->
            if (focused) {
                inputMethodManager.showSoftInput(v, InputMethodManager.SHOW_FORCED)
            } else {
                inputMethodManager.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
    }

    private fun initiateUnlock() {
        val now = System.currentTimeMillis()
        val lastTime = lastUnlockClickTime
        lastUnlockClickTime = now
        if (now - lastTime > maxUnlockClickDelay) {
            unlockClickCount = 1
            return
        } else {
            unlockClickCount++
            if (unlockClickCount < unlockClicksRequired) {
                return
            }
        }
        // TODO configurable code
        val dialog = UnlockDialog(this, model.maintenancePasscode)
        dialog.setFinishListener {
            val activityManager = activityManager
            @Suppress("DEPRECATION")
            if (activityManager.isInLockTaskMode) {
                stopLockTask()
            }
            finishAffinity()
            val info = packageManager.getPackageInfo(ANDROID_LAUNCHER, PackageManager.GET_ACTIVITIES)
            val activity = info.activities.firstOrNull()
            if (activity != null) {
                val name = ComponentName(activity.applicationInfo.packageName, activity.name)
                val intent = Intent(Intent.ACTION_MAIN)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                intent.component = name
                startActivity(intent)
            }
        }
        dialog.show()
   }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        model.refresh()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        model.refresh()
        return super.dispatchKeyEvent(event)
    }

    abstract val unlockButton : View
}