package fi.metatavu.acgpanel

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import kotlinx.android.synthetic.main.activity_identify.*

class IdentifyActivity : PanelActivity() {

    private var locked: Boolean = false

    private val onLogIn = onLogIn@{
        if (locked) {
            return@onLogIn
        }
        locked = true
        greeting_text.text = getString(
            R.string.userGreeting,
            model.currentUser?.userName
        )

        greeting.alpha = 0f
        greeting.visibility = View.VISIBLE
        greeting.animate()
            .alpha(1f)
            .setDuration(300)
            .setListener(null)

        Handler().postDelayed({
            model.canLogInViaRfid = false
            locked = false
            finish()

            val intent = Intent(this, ProductBrowserActivity::class.java)
            startActivity(intent)
        }, 700)
    }

    private val failedLoginListener = {
        UnauthorizedDialog(this).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identify)
        model.canLogInViaRfid = true
        model.addLogInListener(onLogIn)
        model.addFailedLogInListener(failedLoginListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        model.canLogInViaRfid = false
        model.removeFailedLogInListener(failedLoginListener)
        model.removeLogInListener(onLogIn)
    }

    fun browse(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun onIdentify(@Suppress("UNUSED_PARAMETER") view: View) {
        model.logIn("123456789012346")
    }

    override val unlockButton: View
        get() = unlock_button
}
