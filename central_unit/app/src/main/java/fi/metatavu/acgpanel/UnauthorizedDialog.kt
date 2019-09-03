package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import kotlinx.android.synthetic.main.dialog_unauthorized.*

class UnauthorizedDialog(activity: Activity) : Dialog(activity) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_unauthorized)
        ok_button.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, TIMEOUT_IN_MS)
    }

    companion object {
        private const val TIMEOUT_IN_MS = 10L * 1000L
    }
}