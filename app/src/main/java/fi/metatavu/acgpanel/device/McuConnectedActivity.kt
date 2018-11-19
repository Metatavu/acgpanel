package fi.metatavu.acgpanel.device

import android.app.Activity
import android.os.Bundle
import android.hardware.usb.UsbManager
import fi.metatavu.acgpanel.R

class McuConnectedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_mcu_connected)
    }

    override fun onResume() {
        super.onResume()

        val intent = intent
        if (intent != null) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                // Permission granted, nothing else to do here
                finish()
            }
        }
    }
}
