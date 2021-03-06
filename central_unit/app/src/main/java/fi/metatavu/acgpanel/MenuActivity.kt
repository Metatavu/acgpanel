package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLockModel
import fi.metatavu.acgpanel.model.getLoginModel
import fi.metatavu.acgpanel.model.getProductsModel
import kotlinx.android.synthetic.main.activity_menu.*
import kotlin.concurrent.thread

class MenuActivity : PanelActivity() {

    private val productsModel = getProductsModel()
    private val basketModel = getBasketModel()
    private val loginModel = getLoginModel()
    private val lockModel = getLockModel()

    private val loginListener = {
        if (loginModel.loggedIn && loginModel.currentUser?.canShelve != true) {
            quick_pick_button.isEnabled = true
        }
    }

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
        loginModel.addLogInListener(loginListener)
    }

    override fun onPause() {
        loginModel.removeLogInListener(loginListener)
        super.onPause()
    }

    fun browseProducts(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductBrowserActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun browseMap(@Suppress("UNUSED_PARAMETER") view: View) {
        val mapEnabled = PreferenceManager
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
            thread(start = true) {
                basketModel.clearBasket()
                for (page in productsModel.productPages) {
                    for (product in page.products) {
                        basketModel.selectNewBasketItem(product)
                        basketModel.saveSelectedItem(
                            0,
                            loginModel.currentUser?.expenditure ?: "",
                            loginModel.currentUser?.reference ?: ""
                        )
                        if (!lockModel.unsafeIsLineCalibrated(product.line)) {
                            basketModel.disableItemsInLine(product.line)
                        }
                    }
                }
                val intent = Intent(this, QuickPickActivity::class.java)
                runOnUiThread {
                    finish()
                    startActivity(intent)
                }
            }
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
