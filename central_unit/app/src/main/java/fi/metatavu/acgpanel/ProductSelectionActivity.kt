package fi.metatavu.acgpanel

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import fi.metatavu.acgpanel.model.getBasketModel
import fi.metatavu.acgpanel.model.getLockModel
import fi.metatavu.acgpanel.model.getLoginModel
import kotlinx.android.synthetic.main.activity_product_selection.*

class ProductSelectionActivity : PanelActivity() {

    private val basketModel = getBasketModel()
    private val loginModel = getLoginModel()
    private val lockModel = getLockModel()

    override val unlockButton: Button
        get() = unlock_button

    private val logInListener = {
        not_logged_in_warning.visibility = View.INVISIBLE
        expenditure_input.isEnabled = !basketModel.lockUserExpenditure
        expenditure_input.text = loginModel.currentUser?.expenditure ?: ""
        reference_input.isEnabled = !basketModel.lockUserReference
        reference_input.text = loginModel.currentUser?.reference ?: ""
        val basketItem = basketModel.currentBasketItem
        if (basketItem != null) {
            lockModel.isLineCalibrated(basketItem.product.line) { calibrated ->
                runOnUiThread {
                    if (calibrated) {
                        ok_button.isEnabled = true
                    }
                }
            }
        }
    }

    private val failedLoginListener = {
        UnauthorizedDialog(this).show()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
        if (loginModel.loggedIn) {
            not_logged_in_warning.visibility = View.INVISIBLE
        } else {
            not_logged_in_warning.visibility = View.VISIBLE
        }
        loginModel.addLogInListener(logInListener)
        loginModel.addFailedLogInListener(failedLoginListener)
        count_input.setOnKeyListener { view, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (loginModel.loggedIn) {
                    proceed(view)
                }
            }
            false
        }
        val basketItem = basketModel.currentBasketItem
        if (basketItem != null) {
            val product = basketItem.product
            if (product.safetyCard != "") {
                info_button.visibility = View.VISIBLE
            } else {
                info_button.visibility = View.INVISIBLE
            }
            product_name.text = product.name
            product_description.text = "Tuotekoodi: ${product.code}\n\n" +
                                       "Linja: ${product.line}\n\n" +
                                       "Kuvaus:\n${product.productInfo}"
            count_input.text.clear()
            count_input.transformationMethod = null
            count_input.addTextChangedListener(object: TextWatcher{
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString()
                    val value = text?.toIntOrNull()
                    ok_button.isEnabled =
                            loginModel.loggedIn && (
                                (value != null && value >= 1) ||
                                text == "")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }
            })
            if (basketItem.count != 1) {
                count_input.text.insert(0, basketItem.count.toString())
            }
            count_units.text = product.unit
            expenditure_input.isEnabled = !basketModel.lockUserExpenditure
            expenditure_input.text = basketItem.expenditure
            reference_input.isEnabled = !basketModel.lockUserReference
            reference_input.text = basketItem.reference
            drawProduct(product, product_picture)
            lockModel.isLineCalibrated(product.line) { calibrated ->
                runOnUiThread {
                    if (calibrated) {
                        not_calibrated_warning.visibility = View.GONE
                    } else {
                        not_calibrated_warning.visibility = View.VISIBLE
                        ok_button.isEnabled = false
                    }
                }
            }
        }
        if (!loginModel.loggedIn) {
            ok_button.isEnabled = false
        }
    }

    override fun onDestroy() {
        loginModel.removeFailedLogInListener(failedLoginListener)
        loginModel.removeLogInListener(logInListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        count_input.requestFocus()
        loginModel.canLogInViaRfid = true
    }

    override fun onPause() {
        loginModel.canLogInViaRfid = false
        super.onPause()
    }

    @Suppress("UNUSED")
    fun inputExpenditure(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!basketModel.lockUserExpenditure) {
            showEditDialog(getString(R.string.input_expenditure)) {
                expenditure_input.text = it
            }
        }
    }

    @Suppress("UNUSED")
    fun inputReference(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!basketModel.lockUserReference) {
            showEditDialog(getString(R.string.input_reference)) {
                reference_input.text = it
            }
        }
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        basketModel.saveSelectedItem(
            count_input.text.toString().toIntOrNull(),
            expenditure_input.text.toString(),
            reference_input.text.toString()
        )
        val intent = Intent(this, BasketActivity::class.java)
        finish()
        startActivity(intent)
    }

    @Suppress("UNUSED")
    fun showDetails(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductDetailsActivity::class.java)
        startActivity(intent)
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
