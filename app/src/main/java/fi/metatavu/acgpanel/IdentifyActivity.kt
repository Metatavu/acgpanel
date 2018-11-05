package fi.metatavu.acgpanel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import kotlinx.android.synthetic.main.activity_identify.*

class IdentifyActivity : KioskActivity() {
    private var locked: Boolean = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identify)
    }

    fun browse(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        finish();
        startActivity(intent)
    }

    fun identify(@Suppress("UNUSED_PARAMETER") view: View) {
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

    override val unlockButton: View
        get() = unlock_button;
}
