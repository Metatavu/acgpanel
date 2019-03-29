package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.LinkedBlockingQueue

abstract class PromptActivity : Activity() {
    private val queue = LinkedBlockingQueue<String>(1)
    private var process: Thread? = null
    lateinit var handler: Handler
    protected abstract val promptButton: Button
    protected abstract val promptText: TextView
    protected abstract val promptInput: EditText

    protected abstract fun newProcess(): Thread
    fun prompt(prompt: String): String {
        runOnUiThread {
            promptButton.isEnabled = false
            promptText.text = prompt
        }
        handler.postDelayed({
            promptButton.isEnabled = true
        }, 10000)
        return queue.take()
    }

    fun next(@Suppress("UNUSED_PARAMETER") view: View) {
        if (queue.isEmpty()) {
            queue.put(promptInput.text.toString())
            promptInput.text.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(mainLooper)
        findViewById<View>(android.R.id.content)!!.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onResume() {
        super.onResume()
        process = newProcess()
        process!!.start()
    }

    override fun onPause() {
        super.onPause()
        queue.clear()
        process?.interrupt()
        finish()
    }
}