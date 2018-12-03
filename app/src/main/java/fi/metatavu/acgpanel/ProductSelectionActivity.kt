package fi.metatavu.acgpanel

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.method.TransformationMethod
import android.view.View
import android.widget.Button
import fi.metatavu.acgpanel.model.BasketItem
import kotlinx.android.synthetic.main.activity_product_selection.*

class ProductSelectionActivity : PanelActivity() {
    override val unlockButton: Button
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
        val basketItem = model.basket[model.currentProductIndex]
        val product = basketItem.product
        product_name.text = product.name
        product_description.text = product.description
        count_input.transformationMethod = null
        count_input.text.clear()
        if (basketItem.count != 1) {
            count_input.text.insert(0, basketItem.count.toString())
        }
        drawProduct(product, product_picture)
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        val old = model.basket[model.currentProductIndex]
        val new = old.withCount(count_input.text.toString().toIntOrNull() ?: 1)
        model.basket[model.currentProductIndex] = new
        val intent = Intent(this, BasketActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        // TODO refactor to do this in model
        if (model.newItem) {
            model.basket.removeAt(model.currentProductIndex)
            model.newItem = false
        }
        finish()
    }

}
