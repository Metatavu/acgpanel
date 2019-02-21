package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLoginModel
import fi.metatavu.acgpanel.model.getProductsModel
import kotlinx.android.synthetic.main.activity_menu.*

class MenuActivity : PanelActivity() {

    val productsModel = getProductsModel()
    val basketModel = getBasketModel()
    val loginModel = getLoginModel()

    override val unlockButton: Button
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
    }

    override fun onResume() {
        super.onResume()
        if (!loginModel.loggedIn || loginModel.currentUser?.canShelve == true) {
            quick_pick_button.isEnabled = false
        }
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

    fun quickPick(@Suppress("UNUSED_PARAMETER") view: View) {
        productsModel.searchTerm = ""
        productsModel.refreshProductPages {
            for (page in productsModel.productPages) {
                for (product in page.products) {
                    basketModel.selectNewBasketItem(product)
                    basketModel.saveSelectedItem(
                        0,
                        loginModel.currentUser?.expenditure ?: "",
                        loginModel.currentUser?.reference ?: ""
                    )
                }
            }
            val intent = Intent(this, QuickPickActivity::class.java)
            finish()
            startActivity(intent)
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
