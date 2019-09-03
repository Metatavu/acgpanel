package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.Window
import kotlinx.android.synthetic.main.dialog_return.*

class ReturnDialog(activity: Activity) : Dialog(activity) {

    private var goodConditionListener : (() -> Unit)? = null

    private var badConditionListener : ((String) -> Unit)? = null

    fun setGoodConditionListener(listener: () -> Unit) {
        goodConditionListener = listener
    }

    fun setBadConditionListener(listener: (String) -> Unit) {
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
            maintenance_button.visibility = View.VISIBLE
            broken_button.visibility = View.VISIBLE
            unknown_button.visibility = View.VISIBLE
        }
        maintenance_button.setOnClickListener {
            badConditionListener?.invoke("Vaatii huoltoa")
        }
        broken_button.setOnClickListener {
            badConditionListener?.invoke("Fyysisesti rikki")
        }
        unknown_button.setOnClickListener {
            badConditionListener?.invoke("Ei tietoa")
        }
    }
}