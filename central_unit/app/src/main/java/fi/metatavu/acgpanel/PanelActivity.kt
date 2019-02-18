package fi.metatavu.acgpanel

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.inputmethod.InputMethodManager
import fi.metatavu.acgpanel.model.PanelModelImpl
import java.time.Duration

abstract class PanelActivity(private val lockOnStart: Boolean = false)
        : Activity() {

    protected val model = PanelModelImpl

    private val unlockTickCounter = TimedTickCounter(10, Duration.ofSeconds(1)) {
        val dialog = UnlockDialog(this, model.maintenancePasscode)
        dialog.setFinishListener {
            model.isMaintenanceMode = true
            val activityManager = activityManager
            @Suppress("DEPRECATION")
            if (activityManager.isInLockTaskMode) {
                stopLockTask()
            }
            finishAffinity()
            val intent = Intent(this, AppDrawerActivity::class.java)
            startActivity(intent)
        }
        dialog.show()
    }

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
        model.isMaintenanceMode = false
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
        unlockTickCounter.tick()
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