package fi.metatavu.acgpanel.support.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.LOCK_TASK_MODE_NONE
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import java.time.Duration

abstract class LockedDownActivity(private val lockAtStart: Boolean = true)
        : Activity(){

    private val activityManager: ActivityManager
        get() = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val unlockTickCounter = TimedTickCounter(10, Duration.ofSeconds(1)) {
        val dialog = UnlockDialog(this, maintenancePasscode)
        dialog.setFinishListener {
            @Suppress("DEPRECATION")
            if (activityManager.isInLockTaskMode) {
                stopLockTask()
            }
            finishAffinity()
            afterUnlock()
        }
        dialog.show()
    }

    private val rootView: View
        get() = findViewById(android.R.id.content)!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlockButton.setOnClickListener {
            unlockTickCounter.tick()
        }
    }

    override fun onStart() {
        super.onStart()
        rootView.keepScreenOn = true
        rootView.systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (lockAtStart) {
            @Suppress("DEPRECATION")
            if (activityManager.lockTaskModeState == LOCK_TASK_MODE_NONE) {
                startLockTask()
            }
        }
    }

    protected open fun afterUnlock() {

    }

    protected abstract val maintenancePasscode: String

    protected abstract val unlockButton: View

}