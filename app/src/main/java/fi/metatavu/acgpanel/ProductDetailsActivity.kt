package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_product_details.*

class ProductDetailsActivity : PanelActivity() {
    override val unlockButton: View
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_details)
        val product = model.currentBasketItem?.product
        if (product != null) {
            drawProductSafetyCard(product, product_safety_card)
        }
    }

    fun close(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
