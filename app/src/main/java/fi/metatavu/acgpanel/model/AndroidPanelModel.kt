package fi.metatavu.acgpanel.model

import android.os.Handler
import android.os.Looper

object AndroidPanelModel : PanelModel() {
    val handler = Handler(Looper.getMainLooper())

    override fun schedule(callback: Runnable, timeout: Long) {
        handler.postDelayed(callback, timeout)
    }

    override fun unSchedule(callback: Runnable) {
        handler.removeCallbacks(callback)
    }
}