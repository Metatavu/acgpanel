package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import kotlinx.android.synthetic.main.activity_menu.*

class MenuActivity : PanelActivity() {
    override val unlockButton: Button
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
    }

    fun browseProducts(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun browseMap(@Suppress("UNUSED_PARAMETER") view: View) {
        var mapEnabled = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.pref_key_enable_browser), false)
        if (mapEnabled) {
            val intent = Intent(this, WebMapActivity::class.java)
            finish()
            startActivity(intent)
        } else {
            notImplemented(view)
        }
    }

    fun notImplemented(@Suppress("UNUSED_PARAMETER") view: View) {
         AlertDialog.Builder(this)
            .setMessage(R.string.notImplemented)
            .setPositiveButton(R.string.ok, null)
            .create()
            .show()
    }
}
