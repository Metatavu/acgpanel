package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import kotlinx.android.synthetic.main.dialog_profile.ok_button
import kotlinx.android.synthetic.main.dialog_profile.logout_button

class ProfileDialog(activity: Activity) : Dialog(activity) {

    private var logoutListener : (() -> Unit)? = null

    public fun setLogoutListener(listener : () -> Unit) {
        logoutListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_profile)

        logout_button.setOnClickListener {
            val listener = logoutListener
            if (listener != null) {
                listener()
            }
        }
        ok_button.setOnClickListener {
            dismiss()
        }
    }
}