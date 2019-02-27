package fi.metatavu.acgpanel.installer

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import kotlin.concurrent.thread

class MainActivity : Activity() {

    val handler = Handler(Looper.getMainLooper())

    fun print(line: String, error: Boolean = false) {
        runOnUiThread {
            if (error) {
                output_view.append(Html.fromHtml(
                    "<font color=\"#FF0000\"><b>${Html.escapeHtml(line)}</b></font>",
                    Html.FROM_HTML_MODE_COMPACT or
                    Html.FROM_HTML_OPTION_USE_CSS_COLORS))
            } else {
                output_view.append(line)
            }
            output_scroll_view.scrollTo(0, output_view.bottom)
        }
    }

    fun progress(progress: Int) {
        runOnUiThread {
            progress_bar.progress = progress
        }
    }

    fun runCommand(vararg command: String): Int {
        var commandName = command.joinToString(" ")
        print("=== running $commandName...\n")
        val proc = Runtime.getRuntime().exec(command)
        while (proc.isAlive) {
            if (proc.inputStream.available() > 0) {
                print(proc.inputStream.readBytes()
                    .toString(StandardCharsets.ISO_8859_1))
            }
            if (proc.errorStream.available() > 0) {
                print(proc.errorStream.readBytes()
                    .toString(StandardCharsets.ISO_8859_1),
                    error=true)
            }
        }
        val exitValue = proc.exitValue()
        print("=== $commandName exited with value $exitValue\n", error=exitValue != 0)
        return exitValue
    }

    fun writeResource(resId: Int, fileName: String) {
        resources.openRawResource(resId).use { resourceStream ->
            File(fileName).outputStream().use { fileStream ->
                resourceStream.copyTo(fileStream)
            }
        }
        print("=== Written $fileName")
    }

    fun makeProcessThread(): Thread = thread(start = false) {
        try {
            progress(5)
            runCommand("su", "-c", "mkdir /mnt/boot")
            progress(10)
            runCommand("su", "-c", "mount -t vfat /dev/block/mmcblk0p1 /mnt/boot")
            progress(15)
            runCommand("su", "-c", "sed -i 's/^set timeout=5$/set timeout=0/' " +
                    "/mnt/boot/boot/grub/x86_64-efi/grub.cfg")
            progress(20)
            runCommand("su", "-c", "umount /mnt/boot")
            progress(25)
            runCommand("su", "-c", "rmdir /mnt/boot")
            progress(30)
            runCommand("su", "-c", "pm disable com.android.launcher3")
            progress(35)
            runCommand("su", "-c", "pm disable com.farmerbb.taskbar.androidx86")
            progress(40)
            runCommand("su", "-c", "pm disable com.google.android.gms")
            progress(45)
            runCommand("su", "-c", "appops set android TOAST_WINDOW deny")
            progress(50)
            runCommand("su", "-c", "wm overscan 0,-48,0,-72")
            progress(55)
            writeResource(R.raw.bootanimation, "/sdcard/bootanimation.zip")
        } catch (ex: Exception) {
            print("Exception: ${ex.message}", error = true)
        }
    }

    var processThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun buttonClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        if (processThread == null) {
            processThread = makeProcessThread()
            processThread!!.start()
            multi_button.isEnabled = false
            multi_button.text = "OK"
        }
    }
}
