package fi.metatavu.acgpanel

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.renderscript.ScriptGroup
import android.text.InputType
import android.text.method.TransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import fi.metatavu.acgpanel.model.BasketItem
import kotlinx.android.synthetic.main.activity_product_selection.*

class ProductSelectionActivity : PanelActivity() {
    override val unlockButton: Button
        get() = unlock_button

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
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
            expenditure_input.text = basketItem.expenditure
            reference_input.text = basketItem.reference
            drawProduct(product, product_picture)
        }
    }

    fun inputExpenditure(@Suppress("UNUSED_PARAMETER") view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Syötä kustannuspaikka")
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        builder.setView(input)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            expenditure_input.text = input.text
            inputMethodManager.hideSoftInputFromWindow(
                window.decorView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    fun inputReference(@Suppress("UNUSED_PARAMETER") view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Syötä lisäviite")
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        builder.setView(input)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            reference_input.text = input.text
            inputMethodManager.hideSoftInputFromWindow(
                window.decorView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
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

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
