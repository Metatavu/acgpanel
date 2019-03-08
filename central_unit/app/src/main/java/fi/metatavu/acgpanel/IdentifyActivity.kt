package fi.metatavu.acgpanel

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import fi.metatavu.acgpanel.model.getDemoModel
import fi.metatavu.acgpanel.model.getLoginModel
import kotlinx.android.synthetic.main.activity_identify.*

class IdentifyActivity : PanelActivity() {

    private var locked: Boolean = false
    private val loginModel = getLoginModel()
    private val demoModel = getDemoModel()

    private val onLogIn = onLogIn@{
        if (locked) {
            return@onLogIn
        }
        locked = true
        greeting_text.text = getString(
            R.string.userGreeting,
            loginModel.currentUser?.userName
        )

        greeting.alpha = 0f
        greeting.visibility = View.VISIBLE
        greeting.animate()
            .alpha(1f)
            .setDuration(300)
            .setListener(null)

        Handler().postDelayed({
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
        loginModel.addLogInListener(onLogIn)
        loginModel.addFailedLogInListener(failedLoginListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        loginModel.removeFailedLogInListener(failedLoginListener)
        loginModel.removeLogInListener(onLogIn)
    }

    fun browse(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun onIdentify(@Suppress("UNUSED_PARAMETER") view: View) {
        if (demoModel.demoMode) {
            loginModel.logIn("123456789012345")
        }
    }

    override val unlockButton: View
        get() = unlock_button
}
