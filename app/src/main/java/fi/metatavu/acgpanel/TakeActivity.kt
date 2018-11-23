package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_take.*

class TakeActivity : PanelActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take)
    }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        model.logOut()
    }

    override val unlockButton: View
        get() = unlock_button

}
