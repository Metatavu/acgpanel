package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.opengl.Visibility
import android.os.Bundle
import android.view.View
import android.view.Window
import kotlinx.android.synthetic.main.dialog_return.*

class ReturnDialog(activity: Activity) : Dialog(activity) {

    private var goodConditionListener : (() -> Unit)? = null

    private var badConditionListener : ((String) -> Unit)? = null

    public fun setGoodConditionListener(listener: () -> Unit) {
        goodConditionListener = listener
    }

    public fun setBadConditionListener(listener: (String) -> Unit) {
        badConditionListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_return)
        good_condition_button.setOnClickListener {
            goodConditionListener?.invoke()
            dismiss()
        }
        bad_condition_button.setOnClickListener {
            condition_prompt.visibility = View.INVISIBLE
            good_condition_button.visibility = View.INVISIBLE
            bad_condition_button.visibility = View.INVISIBLE
            details_prompt.visibility = View.VISIBLE
            details_input.visibility = View.VISIBLE
            save_button.visibility = View.VISIBLE
        }
        save_button.setOnClickListener {
            badConditionListener?.invoke(details_input.text.toString())
            dismiss()
        }
    }
}