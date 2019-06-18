package fi.metatavu.acgpanel

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import fi.metatavu.acgpanel.model.*
import kotlinx.android.synthetic.main.activity_product_selection.*
import kotlin.concurrent.thread

class ProductSelectionActivity : PanelActivity() {

    private val basketModel = getBasketModel()
    private val loginModel = getLoginModel()
    private val lockModel = getLockModel()
    private val productsModel = getProductsModel()

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
            productsModel.listProductSafetyCards(product) {
                if (it.isNotEmpty()) {
                    info_button.visibility = View.VISIBLE
                } else {
                    info_button.visibility = View.INVISIBLE
                }
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
            if (basketItem.product.borrowable) {
                count_input.visibility = View.INVISIBLE
                count_units.visibility = View.INVISIBLE
                count_label.visibility = View.INVISIBLE
                ok_button.visibility = View.INVISIBLE
                borrow_button.visibility = View.VISIBLE
                return_button.visibility = View.VISIBLE
            } else {
                count_input.visibility = View.VISIBLE
                count_units.visibility = View.VISIBLE
                count_label.visibility = View.VISIBLE
                ok_button.visibility = View.VISIBLE
                borrow_button.visibility = View.INVISIBLE
                return_button.visibility = View.INVISIBLE
            }
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
    }

    fun inputExpenditure(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!basketModel.lockUserExpenditure) {
            if (loginModel.shouldPickUserExpenditure) {
                thread(start = true) {
                    val items = loginModel.listExpenditures()
                    var expenditure: String = items[0]
                    runOnUiThread {
                        if (items.isEmpty()) {
                            showEditDialog(getString(R.string.input_expenditure)) {
                                expenditure_input.text = it
                            }
                        } else {
                            AlertDialog.Builder(this)
                                .setSingleChoiceItems(items.toTypedArray(), 0) { _, i ->
                                    expenditure = items[i]
                                }
                                .setPositiveButton(R.string.ok) { dialog, _ ->
                                    expenditure_input.text = expenditure
                                    dialog.dismiss()
                                }
                                .setNegativeButton(R.string.cancel) { dialog, _ ->
                                    dialog.cancel()
                                }
                                .show()
                        }
                    }
                }
            } else {
                showEditDialog(getString(R.string.input_expenditure)) {
                    expenditure_input.text = it
                }
            }
        }
    }

    fun inputReference(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!basketModel.lockUserReference) {
            showEditDialog(getString(R.string.input_reference)) {
                reference_input.text = it
            }
        }
    }

    fun borrowItem(@Suppress("UNUSED_PARAMETER") view: View) {
        proceed(false, BasketItemType.Borrow)
    }

    fun returnItem(@Suppress("UNUSED_PARAMETER") view: View) {
        val dialog = ReturnDialog(this)
        dialog.setGoodConditionListener {
            proceed(false, BasketItemType.Return, BasketItemCondition.Good)
        }
        dialog.setBadConditionListener {
            proceed(false, BasketItemType.Return, BasketItemCondition.Bad, it)
        }
        dialog.show()
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        val newCount = count_input.text.toString().toIntOrNull()
        val currentBasketItem = basketModel.currentBasketItem
        val oldCount = currentBasketItem?.count
        if (newCount != null &&
                oldCount != null &&
                newCount < oldCount &&
                currentBasketItem.type == BasketItemType.Purchase) {
            AlertDialog.Builder(this)
                .setTitle(R.string.out_of_products_title)
                .setMessage(R.string.out_of_products_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    proceed(markEmpty = true)
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    proceed(markEmpty = false)
                }
                .create()
                .show()
        } else {
            proceed(markEmpty = false)
        }
    }

    fun proceed(
            markEmpty: Boolean,
            type: BasketItemType = BasketItemType.Purchase,
            condition: BasketItemCondition? = null,
            conditionDetails: String? = null) {
        basketModel.saveSelectedItem(
            count_input.text.toString().toIntOrNull(),
            expenditure_input.text.toString(),
            reference_input.text.toString(),
            type,
            condition,
            conditionDetails
        )
        if (markEmpty) {
            basketModel.markCurrentProductEmpty()
        }
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
