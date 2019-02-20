package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import fi.metatavu.acgpanel.model.LoginModel
import kotlinx.android.synthetic.main.dialog_profile.*

class ProfileDialog(activity: Activity, private val model: LoginModel) : Dialog(activity) {

    private var logoutListener : (() -> Unit)? = null

    fun setLogoutListener(listener : () -> Unit) {
        logoutListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_profile)

        content_name.text = model.currentUser?.userName
        content_expenditure.text = model.currentUser?.expenditure

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