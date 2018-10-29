package fi.metatavu.acgpanel

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View

abstract class KioskActivity : Activity() {
    private fun getActivityManager() : ActivityManager {
        return getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager;
    }

    private fun hideUi() {
        // HORRID HACK, if there's a more elegant way to completely
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

        val activityManager = getActivityManager();
        @Suppress("DEPRECATION")
        if (!activityManager.isInLockTaskMode) {
            startLockTask()
        }

        hideUi()

        unlockButton.setOnClickListener { _ -> endKioskMode() }
    }

    fun endKioskMode() {
        val activityManager = getActivityManager();
        @Suppress("DEPRECATION")
        if (activityManager.isInLockTaskMode) {
            stopLockTask()
        }
        finishAffinity()
    }

    abstract val unlockButton : View;
}