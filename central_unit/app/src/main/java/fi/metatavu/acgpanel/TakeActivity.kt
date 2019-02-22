package fi.metatavu.acgpanel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Interpolator
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLockModel
import fi.metatavu.acgpanel.model.getLoginModel
import kotlinx.android.synthetic.main.activity_take.*

class TakeActivity : PanelActivity() {

    private val lockModel = getLockModel()
    private val basketModel = getBasketModel()
    private val loginModel = getLoginModel()

    private val alarmCallback = Runnable {
        alarm_overlay_back.visibility = View.VISIBLE
        alarm_overlay_front.visibility = View.VISIBLE
    }

    var handler = Handler(Looper.getMainLooper())

    val onLockOpenedListener = listener@{
        status_text.text = getString(
            R.string.complete_by_closing_door,
            lockModel.currentLock,
            lockModel.numLocks)
        handler.removeCallbacks(alarmCallback)
        handler.postDelayed(alarmCallback, ALARM_TIMEOUT)
        return@listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take)
        onLockOpenedListener()
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
        lockModel.addLockOpenedListener(onLockOpenedListener)
        handler.postDelayed(alarmCallback, ALARM_TIMEOUT)
    }

    override fun onPause() {
        handler.removeCallbacks(alarmCallback)
        lockModel.removeLockOpenedListener(onLockOpenedListener)
        super.onPause()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        basketModel.completeProductTransaction {
            loginModel.logOut()
        }
    }

    fun back(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    override val unlockButton: View
        get() = unlock_button

    companion object {
        private const val ALARM_TIMEOUT = 80L*1000L
    }

}
