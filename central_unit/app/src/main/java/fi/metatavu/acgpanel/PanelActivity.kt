package fi.metatavu.acgpanel

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import fi.metatavu.acgpanel.model.getLoginModel
import fi.metatavu.acgpanel.model.getMaintenanceModel
import fi.metatavu.acgpanel.model.getNotificationModel
import java.time.Duration

abstract class PanelActivity(private val lockAtStart: Boolean = false)
        : Activity() {

    private val maintenanceModel = getMaintenanceModel()
    private val loginModel = getLoginModel()
    private val notificationModel = getNotificationModel()
    private val handler = Handler(Looper.getMainLooper())

    private val unlockTickCounter = TimedTickCounter(10, Duration.ofSeconds(1)) {
        val dialog = UnlockDialog(this, maintenanceModel.maintenancePasscode)
        dialog.setFinishListener {
            maintenanceModel.isMaintenanceMode = true
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
        DeviceErrorDialog(this, msg, maintenanceModel).show()
    }

    private val onNotification = { msg: String ->
        notificationBar?.text = msg
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
        if (lockAtStart) {
            val activityManager = activityManager
            @Suppress("DEPRECATION")
            if (!activityManager.isInLockTaskMode) {
                startLockTask()
            }
        }
        unlockButton.setOnClickListener { initiateUnlock() }
        loginModel.addLogOutListener(onLogout)
    }

    override fun onDestroy() {
        loginModel.removeLogOutListener(onLogout)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        maintenanceModel.addDeviceErrorListener(onDeviceError)
        maintenanceModel.isMaintenanceMode = false
        notificationModel.addNotificationListener(onNotification)
        notificationModel.refreshNotifications()
    }

    override fun onPause() {
        maintenanceModel.removeDeviceErrorListener(onDeviceError)
        notificationModel.removeNotificationListener(onNotification)
        super.onPause()
    }

    @Suppress("unused")
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
        loginModel.refresh()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        loginModel.refresh()
        return super.dispatchKeyEvent(event)
    }

    protected fun showEditDialog(title: String, onConfirm: (String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        builder.setView(input)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            onConfirm(input.text.toString())
            handler.postDelayed({
                inputMethodManager.hideSoftInputFromWindow(
                    window.decorView.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }, 50)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        val dialog = builder.create()
        input.setOnKeyListener { _, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP) {
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick()
                }
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).callOnClick()
                }
            }
            false
        }
        dialog.show()
    }

    abstract val unlockButton : View

    protected open val notificationBar: TextView? = null
}