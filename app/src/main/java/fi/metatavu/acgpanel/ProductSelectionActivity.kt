package fi.metatavu.acgpanel

import android.os.Bundle
import android.view.View
import android.widget.Button
import fi.metatavu.acgpanel.model.PanelModel
import kotlinx.android.synthetic.main.activity_product_selection.*

class ProductSelectionActivity : PanelActivity() {
    override val unlockButton: Button
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
        root.requestFocus()
        amount_input.showSoftInputOnFocus = false
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
