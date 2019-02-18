package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_product_selection.*

class ProductSelectionActivity : PanelActivity() {
    override val unlockButton: Button
        get() = unlock_button

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private val logInListener = {
        not_logged_in_warning.visibility = View.INVISIBLE
        ok_button.isEnabled = true
        expenditure_input.isEnabled = !model.lockUserExpenditure
        expenditure_input.text = model.currentUser?.expenditure ?: ""
        reference_input.isEnabled = !model.lockUserReference
        reference_input.text = model.currentUser?.reference ?: ""
    }

    private val failedLoginListener = {
        UnauthorizedDialog(this).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
        if (model.loggedIn) {
            not_logged_in_warning.visibility = View.INVISIBLE
        } else {
            not_logged_in_warning.visibility = View.VISIBLE
        }
        model.addLogInListener(logInListener)
        model.addFailedLogInListener(failedLoginListener)
        count_input.setOnKeyListener { view, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (model.loggedIn) {
                    proceed(view)
                }
            }
            false
        }
        val basketItem = model.currentBasketItem
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
            count_input.text.clear()
            count_input.addTextChangedListener(object: TextWatcher{
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString()
                    val value = text?.toIntOrNull()
                    ok_button.isEnabled =
                            model.loggedIn && (
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
            expenditure_input.isEnabled = !model.lockUserExpenditure
            expenditure_input.text = basketItem.expenditure
            reference_input.isEnabled = !model.lockUserReference
            reference_input.text = basketItem.reference
            drawProduct(product, product_picture)
        }
        if (!model.loggedIn) {
            ok_button.isEnabled = false
        }
    }

    override fun onDestroy() {
        model.removeFailedLogInListener(failedLoginListener)
        model.removeLogInListener(logInListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        count_input.requestFocus()
        model.canLogInViaRfid = true
    }

    override fun onPause() {
        model.canLogInViaRfid = false
        super.onPause()
    }

    private fun showEditDialog(title: String, onConfirm: (String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        builder.setView(input)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            onConfirm(input.text.toString())
            inputMethodManager.hideSoftInputFromWindow(
                window.decorView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        val dialog = builder.create()
        input.setOnKeyListener { _, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP) {
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick()
                }
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).callOnClick()
                }
            }
            false
        }
        dialog.show()

    }

    @Suppress("UNUSED")
    fun inputExpenditure(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!model.lockUserExpenditure) {
            showEditDialog(getString(R.string.input_expenditure)) {
                expenditure_input.text = it
            }
        }
    }

    @Suppress("UNUSED")
    fun inputReference(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!model.lockUserReference) {
            showEditDialog(getString(R.string.input_reference)) {
                reference_input.text = it
            }
        }
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        model.saveSelectedItem(
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
