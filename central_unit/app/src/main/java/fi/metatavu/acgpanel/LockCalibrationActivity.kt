package fi.metatavu.acgpanel

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import fi.metatavu.acgpanel.model.getLockModel
import kotlinx.android.synthetic.main.activity_prompt.*
import kotlin.concurrent.thread

class LockCalibrationActivity : PromptActivity() {
    val model = getLockModel()
    override val promptButton: Button
        get() = prompt_button
    override val promptText: TextView
        get() = prompt_text
    override val promptInput: EditText
        get() = prompt_input

    protected override fun newProcess() = thread(start = false, isDaemon = true) {
        try {
            model.isCalibrationMode = true
            val numShelves = prompt("Montako ohjausyksikköä kaapissa on?").toInt()
            for (shelf in 1..numShelves) {
                prompt(
                    "Ohjain $shelf/$numShelves\n" +
                            "Sulje kaikki luukut." +
                            " Paina ohjausyksikön $shelf nappia," +
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

        } finally {
            model.isCalibrationMode = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt)
    }

}
