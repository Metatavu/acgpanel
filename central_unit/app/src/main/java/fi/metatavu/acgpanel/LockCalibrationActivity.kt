package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import fi.metatavu.acgpanel.model.getLockModel
import kotlinx.android.synthetic.main.activity_lock_calibration.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class LockCalibrationActivity : Activity() {
    val model = getLockModel()
    private val queue = LinkedBlockingQueue<String>(1)
    private var process: Thread? = null
    lateinit var handler: Handler

    private fun newProcess() = thread(start = false, isDaemon = true) {
        try {
            val numShelves = prompt("Montako ohjausyksikköä kaapissa on?").toInt()
            for (shelf in 1..numShelves) {
                prompt(
                    "Ohjain $shelf/$numShelves\n" +
                            "Paina ohjausyksikön $shelf nappia," +
                            " varmista että valo vilkkuu," +
                            " ja valitse \"Seuraava\"."
                )
                model.calibrationAssignShelf(shelf)
                prompt(
                    "Ohjain $shelf/$numShelves\n" +
                            "Paina nappia uudestaan ja varmista," +
                            " että vilkkuminen loppuu."
                )
                for (compartment in 1..12) {
                    model.openSpecificLock(shelf, compartment, reset=true)
                    val line = prompt(
                        "Ohjain $shelf/$numShelves\n" +
                                "Luukku $compartment/12\n" +
                                "\nSyötä auenneen luukun linjanumero. Jos mikään luukku ei" +
                                " auennut, jätä kenttä tyhjäksi. Älä sulje vielä luukkua."
                    )
                    if (line != "") {
                        model.calibrationAssignLine(line, shelf, compartment)
                    }
                }
            }
            prompt(
                "Kalibrointi on valmis. Voit nyt sulkea luukut." +
                        " Jos haluat liittää usean linjan samaan" +
                        " luukkuun, aja kalibrointi uudestaan."
            )
            runOnUiThread { finish() }
        } catch (ex: InterruptedException) {

        }
    }

    fun prompt(prompt: String): String {
        runOnUiThread {
            prompt_button.isEnabled = false
            prompt_text.text = prompt
        }
        handler.postDelayed({
            prompt_button.isEnabled = true
        }, 10000)
        return queue.take()
    }

    fun next(@Suppress("UNUSED_PARAMETER") view: View) {
        if (queue.isEmpty()) {
            queue.put(prompt_input.text.toString())
            prompt_input.text.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(mainLooper)
        setContentView(R.layout.activity_lock_calibration)
        findViewById<View>(android.R.id.content)!!.systemUiVisibility =
                SYSTEM_UI_FLAG_LAYOUT_STABLE
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
