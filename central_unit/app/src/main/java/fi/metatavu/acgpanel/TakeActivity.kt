package fi.metatavu.acgpanel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import kotlinx.android.synthetic.main.activity_take.*

class TakeActivity : PanelActivity() {

    val alarmCallback = Runnable {
        alarm_overlay_back.visibility = View.VISIBLE
        alarm_overlay_front.visibility = View.VISIBLE
    }

    var handler = Handler(Looper.getMainLooper())

    val onLockOpenListener = listener@{
        status_text.text = getString(
            R.string.complete_by_closing_door,
            model.nextLockToOpen,
            model.basket.size)
        handler.removeCallbacks(alarmCallback)
        handler.postDelayed(alarmCallback, ALARM_TIMEOUT)
        return@listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take)
        onLockOpenListener()
    }

    override fun onStart() {
        super.onStart()
        val blink = AlphaAnimation(1f, 0f)
        blink.duration = 1000
        blink.interpolator = Interpolator {
            if (it < 0.5f) {0f} else {1f}
        }
        blink.repeatCount = Animation.INFINITE
        alarm_overlay_front.animation = blink
        alarm_overlay_front.visibility = View.GONE
        alarm_overlay_back.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        model.addLockOpenListener(onLockOpenListener)
        handler.postDelayed(alarmCallback, ALARM_TIMEOUT)
    }

    override fun onPause() {
        handler.removeCallbacks(alarmCallback)
        model.removeLockOpenListener(onLockOpenListener)
        super.onPause()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        model.completeProductTransaction {
            model.logOut()
        }
    }

    fun back(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    override val unlockButton: View
        get() = unlock_button

    companion object {
        private val ALARM_TIMEOUT = 120L*1000L
    }

}
