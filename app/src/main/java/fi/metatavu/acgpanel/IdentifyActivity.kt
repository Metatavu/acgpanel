package fi.metatavu.acgpanel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.view.View
import kotlinx.android.synthetic.main.activity_identify.*

class IdentifyActivity : KioskActivity() {
    private var locked: Boolean = false;

    private val broadcastManager: LocalBroadcastManager
        get() = LocalBroadcastManager.getInstance(this)

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            identify()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identify)

        broadcastManager.registerReceiver(broadcastReceiver, IntentFilter(ID_CARD_READ_INTENT))
    }

    override fun onDestroy() {
        super.onDestroy()

        broadcastManager.unregisterReceiver(broadcastReceiver)
    }

    fun browse(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        finish();
        startActivity(intent)
    }

    fun identify() {
        if (locked) {
            return;
        }

        locked = true;
        greeting.alpha = 0f;
        greeting.visibility = View.VISIBLE;
        greeting.animate()
            .alpha(1f)
            .setDuration(300)
            .setListener(null)

        Handler().postDelayed({
            val intent = Intent(this, ProductBrowserActivity::class.java)
            finish();
            locked = false;
            startActivity(intent)
        }, 700)
    }

    fun onIdentify(@Suppress("UNUSED_PARAMETER") view: View) {
        identify()
    }

    override val unlockButton: View
        get() = unlock_button;
}
