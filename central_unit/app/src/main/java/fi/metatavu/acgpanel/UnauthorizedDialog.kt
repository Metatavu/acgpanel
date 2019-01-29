package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import fi.metatavu.acgpanel.model.PanelModel
import kotlinx.android.synthetic.main.dialog_profile.*

class UnauthorizedDialog(activity: Activity) : Dialog(activity) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_unauthorized)
        ok_button.setOnClickListener {
            dismiss()
        }
    }
}