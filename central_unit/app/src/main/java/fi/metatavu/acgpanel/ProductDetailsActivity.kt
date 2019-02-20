package fi.metatavu.acgpanel

import android.os.Bundle
import android.view.View
import fi.metatavu.acgpanel.model.getBasketModel
import kotlinx.android.synthetic.main.activity_product_details.*

class ProductDetailsActivity : PanelActivity() {
    override val unlockButton: View
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_details)
        val product = getBasketModel().currentBasketItem?.product
        if (product != null) {
            product_safety_card.url = "$PRODUCT_IMAGE_PREFIX/UserAssets/${product.safetyCard}"
        }
    }

    fun close(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    fun nextPage(@Suppress("UNUSED_PARAMETER") view: View) {
        if (product_safety_card.page < product_safety_card.numPages - 1) {
            product_safety_card.page++
        }
    }

    fun previousPage(@Suppress("UNUSED_PARAMETER") view: View) {
        if (product_safety_card.page > 0) {
            product_safety_card.page--
        }
    }




}
