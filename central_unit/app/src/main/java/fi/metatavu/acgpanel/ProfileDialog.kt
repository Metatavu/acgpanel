package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.view.Window
import fi.metatavu.acgpanel.model.BasketModel
import fi.metatavu.acgpanel.model.LoginModel
import kotlinx.android.synthetic.main.dialog_profile.*
import kotlin.concurrent.thread

class ProfileDialog(
        activity: Activity,
        private val loginModel: LoginModel,
        private val basketModel: BasketModel
) : Dialog(activity) {

    private var logoutListener : (() -> Unit)? = null

    fun setLogoutListener(listener : () -> Unit) {
        logoutListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_profile)

        val user = loginModel.currentUser
        content_name.text = user?.userName
        content_expenditure.text = user?.expenditure
        thread(start = true) {
            if (user != null) {
                val text = basketModel.userBorrowedProducts(user)
                    .joinToString("\n") { it.name }
                Handler(context.mainLooper).post {
                    content_borrows.text =
                        if (text != "")
                            text
                        else
                            context.getString(R.string.profile_no_borrows)
                }
            } else {
                content_borrows.text = context.getString(R.string.profile_no_borrows)
            }
        }

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