package fi.metatavu.acgpanel.support.ui

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import fi.metatavu.acgpanel.support.R
import kotlinx.android.synthetic.main.dialog_unlock.*

class UnlockDialog(activity: Activity, val code: String) : Dialog(activity) {

    private var finishListener : (() -> Unit)? = null

    fun setFinishListener(listener : () -> Unit) {
        finishListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_unlock)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                ok_button.isEnabled = s?.toString() == code
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        ok_button.setOnClickListener {
            val listener = finishListener
            if (listener != null) {
                listener()
            }
        }
        cancel_button.setOnClickListener { dismiss() }
    }
}