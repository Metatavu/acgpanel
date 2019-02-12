package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import fi.metatavu.acgpanel.model.PanelModel
import kotlinx.android.synthetic.main.dialog_device_error.*

class DeviceErrorDialog(activity: Activity, val message: String, val model: PanelModel) : Dialog(activity) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_device_error)
        message_label.text = message
        ok_button.setOnClickListener {
            model.isDeviceErrorMode = false
            dismiss()
        }
    }
}