package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
        count_input.setOnKeyListener listener@{ view, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                proceed(view)
                return@listener true
            }
            return@listener false
        }
        val basketItem = model.currentBasketItem
        if (basketItem != null) {
            val product = basketItem.product
            product_name.text = product.name
            product_description.text = product.description
            count_input.transformationMethod = null
            count_input.text.clear()
            if (basketItem.count != 1) {
                count_input.text.insert(0, basketItem.count.toString())
            }
            count_units.text = product.unit
            expenditure_input.text = basketItem.expenditure
            reference_input.text = basketItem.reference
            drawProduct(product, product_picture)
        }
    }

    override fun onResume() {
        super.onResume()
        count_input.requestFocus()
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
        input.setOnKeyListener listener@{ view, _, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_UP) {
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick()
                    return@listener true
                }
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).callOnClick()
                    return@listener true
                }
            }
            return@listener false
        }
        dialog.show()

    }

    fun inputExpenditure(@Suppress("UNUSED_PARAMETER") view: View) {
        showEditDialog(getString(R.string.input_expenditure)) {
            expenditure_input.text = it
        }
    }

    fun inputReference(@Suppress("UNUSED_PARAMETER") view: View) {
        showEditDialog(getString(R.string.input_reference)) {
            reference_input.text = it
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

    fun showDetails(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, ProductDetailsActivity::class.java)
        startActivity(intent)
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
