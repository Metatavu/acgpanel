package fi.metatavu.acgpanel.installer

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

class MainActivity : Activity() {

    sealed class State {
        object Ready: State()
        object Running: State()
        object Paused: State()
        object Finished: State()
    }

    val handler = Handler(Looper.getMainLooper())
    val stateNotifier = Object()
    var state: State = State.Ready

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

    var maxProgress: Int = 100
        set(value) {
            runOnUiThread {
                progress_bar.max = value
            }
            field = value
        }

    fun incrementProgress() {
        runOnUiThread {
            progress_bar.incrementProgressBy(1)
        }
    }

    fun runCommand(command: String): Int {
        print("=== running $command...\n")
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
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
        print("=== $command exited with value $exitValue\n", error=exitValue != 0)
        return exitValue
    }

    fun pause() {
        state = State.Paused
        runOnUiThread {
            multi_button.text = getString(R.string.resume)
            multi_button.isEnabled = true
        }
        synchronized(stateNotifier) {
            while (state == State.Paused) {
                stateNotifier.wait()
            }
        }
    }

    fun completed() {
        runOnUiThread {
            state = State.Finished
            multi_button.isEnabled = true
            multi_button.text = getString(R.string.ok)
        }
    }

    fun writeResource(resId: Int, fileName: String) {
        resources.openRawResource(resId).use {
            Files.copy(it, Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING)
            print("=== Written $fileName\n")
        }
    }

    fun makeProcessThread(): Thread = thread(start = false) {
        try {
            maxProgress = 38
            // Disable GRUB loading screen
            runCommand("mkdir /mnt/boot")
            incrementProgress()
            runCommand("mount -t vfat /dev/block/mmcblk0p1 /mnt/boot")
            incrementProgress()
            runCommand("sed -i 's/^set timeout=5$/set timeout=0/' " +
                    "/mnt/boot/boot/grub/x86_64-efi/grub.cfg")
            incrementProgress()
            runCommand("umount /mnt/boot")
            incrementProgress()
            runCommand("rmdir /mnt/boot")
            incrementProgress()
            // Install ACGPanel as privileged application
            val appApkPath = File(filesDir, "app.apk").absolutePath
            writeResource(R.raw.app, appApkPath)
            incrementProgress()
            runCommand("mkdir /system/priv-app/fi.metatavu.acgpanel")
            incrementProgress()
            runCommand("chmod 755 /system/priv-app/fi.metatavu.acgpanel")
            incrementProgress()
            runCommand("cp $appApkPath /system/priv-app/fi.metatavu.acgpanel/")
            incrementProgress()
            runCommand("chmod 644 /system/priv-app/fi.metatavu.acgpanel/app.apk")
            incrementProgress()
            runCommand("pm install $appApkPath")
            incrementProgress()
            // Start ACGPanel configuration
            val panelSettingsComponent = ComponentName.unflattenFromString(
                "fi.metatavu.acgpanel/.AcgPanelSettingsActivity")
            val panelSettingsIntent = Intent(Intent.ACTION_VIEW)
            panelSettingsIntent.component = panelSettingsComponent
            startActivity(panelSettingsIntent)
            pause()
            incrementProgress()
            // Configure app as device owner
            runCommand("dpm set-device-owner fi.metatavu.acgpanel/.DeviceAdminReceiver")
            incrementProgress()
            // Disable other launchers so the user can't muck around
            runCommand("pm disable com.android.launcher3")
            incrementProgress()
            runCommand("pm disable com.farmerbb.taskbar.androidx86")
            incrementProgress()
            // Disable home key
            val genericKlPath = File(filesDir, "Generic.kl").absolutePath
            writeResource(R.raw.generic, genericKlPath)
            incrementProgress()
            runCommand("cp $genericKlPath /system/usr/keylayout/Generic.kl")
            incrementProgress()
            runCommand("chmod 644 /system/usr/keylayout/Generic.kl")
            incrementProgress()
            // Disable Google Mobile Services (Chrome, Play etc)
            runCommand("pm disable com.google.android.gms")
            incrementProgress()
            // Install non-emoji keyboard
            val simpleKeyboardApkPath = File(filesDir, "simplekeyboard.apk").absolutePath
            writeResource(R.raw.simplekeyboard, simpleKeyboardApkPath)
            incrementProgress()
            runCommand("pm install $simpleKeyboardApkPath")
            incrementProgress()
            runCommand("ime enable rkr.simplekeyboard.inputmethod/.latin.LatinIME")
            incrementProgress()
            runCommand("ime disable com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime.VoiceInputMethodService")
            incrementProgress()
            runCommand("ime disable com.android.inputmethod.latin/.LatinIME")
            incrementProgress()
            // Hide snackbars and toasts
            runCommand("appops set android TOAST_WINDOW deny")
            incrementProgress()
            // Hide top and bottom bars
            runCommand("wm overscan 0,-48,0,-72")
            incrementProgress()
            // Install logo as boot animation
            val bootAnimationPath = File(filesDir, "bootanimation.zip").absolutePath
            writeResource(R.raw.bootanimation, bootAnimationPath)
            incrementProgress()
            runCommand("cp $bootAnimationPath /system/media")
            incrementProgress()
            runCommand("chmod 644 /system/media/bootanimation.zip")
            incrementProgress()
            // Black background to remove awkward flash after boot animation
            WallpaperManager.getInstance(applicationContext)
                            .setResource(R.raw.background)
            incrementProgress()
            // Install F-Droid privileged extension to get auto updates
            val fDroidPrivilegedApkPath = File(filesDir, "fdroid_privileged.apk").absolutePath
            writeResource(R.raw.fdroid_privileged, fDroidPrivilegedApkPath)
            incrementProgress()
            runCommand("mkdir /system/priv-app/org.fdroid.fdroid.privileged")
            incrementProgress()
            runCommand("chmod 755 /system/priv-app/org.fdroid.fdroid.privileged")
            incrementProgress()
            runCommand("cp $fDroidPrivilegedApkPath /system/priv-app/org.fdroid.fdroid.privileged/fdroid_privileged.apk")
            incrementProgress()
            runCommand("chmod 644 /system/priv-app/org.fdroid.fdroid.privileged/fdroid_privileged.apk")
            incrementProgress()
            runCommand("pm install $fDroidPrivilegedApkPath")
            incrementProgress()
            // Install F-Droid for update management
            val fDroidApkPath = File(filesDir, "fdroid.apk").absolutePath
            writeResource(R.raw.fdroid, fDroidApkPath)
            incrementProgress()
            runCommand("pm install $fDroidApkPath")
            incrementProgress()
            // Add update repo
            val addRepoIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("fdroidrepo://static.metatavu.io/acgpanel_repo/repo/"))
            startActivity(addRepoIntent)
            incrementProgress()
            completed()
        } catch (ex: Exception) {
            print("Exception: ${ex.javaClass.name}: ${ex.message}", error = true)
            completed()
        }
    }

    var processThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun buttonClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        when (state) {
            State.Ready -> {
                state = State.Running
                processThread?.interrupt()
                processThread = makeProcessThread()
                processThread!!.start()
                multi_button.isEnabled = false
            }
            State.Paused -> {
                state = State.Running
                synchronized(stateNotifier) {
                    stateNotifier.notifyAll()
                }
                multi_button.isEnabled = false
            }
            State.Finished -> {
                runCommand("reboot")
            }
        }
    }
}
