package fi.metatavu.acgpanel

import android.content.Intent
import android.os.Bundle
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
        product_name.text = model.currentProduct?.name
        product_description.text = model.currentProduct?.description
        if (model.currentProduct != null) {
            drawProduct(model.currentProduct!!, product_picture)
        }
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        val product = model.currentProduct
        if (product != null) {
            model.basket.add(
                BasketItem(
                    product,
                    count_input.text.toString().toIntOrNull() ?: 1,
                    "EPS200",
                    "Matti Meikäläinen"
                )
            )
        }

        val intent = Intent(this, BasketActivity::class.java)
        finish()
        startActivity(intent)
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
