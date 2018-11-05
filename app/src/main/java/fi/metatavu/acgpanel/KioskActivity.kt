package fi.metatavu.acgpanel

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View

abstract class KioskActivity : Activity() {

    private var lastUnlockClickTime = Long.MIN_VALUE
    private var unlockClickCount = 0
    private val maxUnlockClickDelay = 1000
    private val unlockClicksRequired = 10

    private fun getActivityManager() : ActivityManager {
        return getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager;
    }

    private fun hideUi() {
        // If there's a more elegant way to completely
        // hide the system UI, get rid of this. Sticky Immersive Mode is
        // not it, the UI is still shown on swipe from border
        val decorView = window.decorView;
        decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_IMMERSIVE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        decorView.postOnAnimation { hideUi() }
    }

    override fun onStart() {
        super.onStart()
        isImmersive = true
        findViewById<View>(android.R.id.content)!!.keepScreenOn = true

        val activityManager = getActivityManager();
        @Suppress("DEPRECATION")
        if (!activityManager.isInLockTaskMode) {
            startLockTask()
        }

        hideUi()

        unlockButton.setOnClickListener { _ -> initiateUnlock(); }
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
        val dialog = UnlockDialog(this, "0000")
        dialog.setFinishListener {
            val activityManager = getActivityManager();
            @Suppress("DEPRECATION")
            if (activityManager.isInLockTaskMode) {
                stopLockTask()
            }
            finishAffinity()
            val info = packageManager.getPackageInfo("com.android.launcher", PackageManager.GET_ACTIVITIES)
            val activity = info.activities.firstOrNull()
            if (activity != null) {
                val name = ComponentName(activity.applicationInfo.packageName, activity.name);
                val intent = Intent(Intent.ACTION_MAIN)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                intent.component = name;
                startActivity(intent)
            }
        }
        dialog.show()
   }

    abstract val unlockButton : View;
}