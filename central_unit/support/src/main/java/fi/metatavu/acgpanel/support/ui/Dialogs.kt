package fi.metatavu.acgpanel.support.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import fi.metatavu.acgpanel.support.R
import kotlin.math.roundToInt

fun Activity.showEditDialog(
        title: String,
        onConfirm: (String) -> Unit,
        inputFieldPadding: Float = 20f,
        inputFieldTextSize: Float = 30f,
        titleTextSize: Float = 25f,
        buttonsTextSize: Float = 25f) {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE)
        as InputMethodManager
    val builder = AlertDialog.Builder(this)
    builder.setTitle(title)
    val input = EditText(this)
    val dp = resources.displayMetrics.density;
    input.textSize = inputFieldTextSize*dp
    input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    builder.setPositiveButton(R.string.ok) { dialog, _ ->
        onConfirm(input.text.toString())
        inputMethodManager.hideSoftInputFromWindow(
            input.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
        dialog.dismiss()
    }
    builder.setNegativeButton(R.string.cancel) { dialog, _ ->
        inputMethodManager.hideSoftInputFromWindow(
            input.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
        dialog.cancel()
    }
    val dialog = builder.create()
    dialog.setView(
        input,
        (inputFieldPadding*dp).roundToInt(),
        (inputFieldPadding*dp).roundToInt(),
        (inputFieldPadding*dp).roundToInt(),
        (inputFieldPadding*dp).roundToInt())
    input.setOnKeyListener { _, _, keyEvent ->
        if (keyEvent.action == KeyEvent.ACTION_UP) {
            if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick()
            }
            if (keyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).callOnClick()
            }
        }
        false
    }
    dialog.show()
    dialog.findViewById<Button>(android.R.id.button1)?.textSize = buttonsTextSize*dp
    dialog.findViewById<Button>(android.R.id.button2)?.textSize = buttonsTextSize*dp
    dialog.findViewById<Button>(android.R.id.title)?.textSize = titleTextSize*dp
}

