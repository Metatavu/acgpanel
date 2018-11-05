package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import kotlinx.android.synthetic.main.activity_product_selection.*

class ProductSelectionActivity : KioskActivity() {
    override val unlockButton: Button
        get() = unlock_button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

}
