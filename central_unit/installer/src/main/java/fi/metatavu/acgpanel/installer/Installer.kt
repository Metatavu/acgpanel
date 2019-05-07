package fi.metatavu.acgpanel.installer

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.text.Html
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

/**
 * A program that installs ACGPanel or Cotio as a COSU app and
 * configures the system for single use. The program won't
 * function properly unless installed with this.
 */
class MainActivity : Activity() {

    /**
     * Possible states for the installation process
     */
    private sealed class State {
        object Ready: State()
        object Running: State()
        object Paused: State()
        object Finished: State()
        object Errored: State()
    }

    private sealed class InstallTarget {
        object ACGPanel : InstallTarget()
        object Cotio : InstallTarget()
    }

    /**
     * This object is notified when the state
     * is changed from State.Paused to something else
     */
    private val stateNotifier = Object()

    /**
     * The state of the installation process
     */
    private var state: State = State.Ready

    /**
     * The program to install
     */
    private var installTarget: InstallTarget = InstallTarget.ACGPanel

    /**
     * Output a line to the screen and scroll to bottom
     */
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

    /**
     * The number of steps of the installation process
     */
    var maxProgress: Int = 100
        set(value) {
            runOnUiThread {
                progress_bar.max = value
            }
            field = value
        }

    /**
     * Move to next step in the installation process
     */
    fun incrementProgress() {
        runOnUiThread {
            progress_bar.incrementProgressBy(1)
        }
    }

    /** Run a command, block until it finishes, and forward its output to the screen.
     * Commands that error do *not* throw an exception, but the error is shown
     * on the screen.
     */
    fun runCommand(command: String, incrementProgress: Boolean = true): Int {
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
        if (incrementProgress) {
            incrementProgress()
        }
        return exitValue
    }

    /**
     * Set the installation to paused state and block as long as it is
     * in paused.
     */
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

    /**
     * Mark the installation as completed or errored.
     */
    fun completed(success: Boolean = true) {
        runOnUiThread {
            state = if (success) State.Finished else State.Errored
            multi_button.isEnabled = true
            multi_button.text = getString(R.string.ok)
        }
    }

    /**
     * Convert a raw resource into a file in the file system
     */
    fun writeResource(resId: Int, fileName: String, incrementProgress: Boolean = true) {
        resources.openRawResource(resId).use {
            Files.copy(it, Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING)
            if (incrementProgress) {
                incrementProgress()
            }
            print("=== Written $fileName\n")
        }
    }

    /**
     * Modify the GRUB configuration file in the EFI partition to
     * set menu timeout to 0
     */
    private fun disableGrubMenuScreen() {
        runCommand("mkdir /mnt/boot")
        runCommand("mount -t vfat /dev/block/mmcblk0p1 /mnt/boot")
        runCommand(
            "sed -i 's/^set timeout=5$/set timeout=0/' " +
                    "/mnt/boot/boot/grub/x86_64-efi/grub.cfg"
        )
        runCommand("umount /mnt/boot")
        runCommand("rmdir /mnt/boot")
    }

    /**
     * Install the bundled app.apk file into /system/priv-app/ with
     * proper privileges
     */
    private fun installMainAppAsPrivileged() {
        val appApkPath = File(filesDir, "app.apk").absolutePath
        writeResource(R.raw.app, appApkPath)
        runCommand("mkdir /system/priv-app/fi.metatavu.acgpanel")
        runCommand("chmod 755 /system/priv-app/fi.metatavu.acgpanel")
        runCommand("cp $appApkPath /system/priv-app/fi.metatavu.acgpanel/")
        runCommand("chmod 644 /system/priv-app/fi.metatavu.acgpanel/app.apk")
        runCommand("pm install $appApkPath")
        runCommand("pm grant fi.metatavu.acgpanel android.permission.READ_EXTERNAL_STORAGE")
        runCommand("pm grant fi.metatavu.acgpanel android.permission.ACCESS_SUPERUSER")
    }

    /**
     * Install the bundled cotio.apk file into /system/priv-app/ with
     * proper privileges
     */
    private fun installCotioAppAsPrivileged() {
        val cotioApkPath = File(filesDir, "cotio.apk").absolutePath
        writeResource(R.raw.cotio, cotioApkPath)
        runCommand("mkdir /system/priv-app/fi.metatavu.acgpanel.cotio")
        runCommand("chmod 755 /system/priv-app/fi.metatavu.acgpanel.cotio")
        runCommand("cp $cotioApkPath /system/priv-app/fi.metatavu.acgpanel.cotio/")
        runCommand("chmod 644 /system/priv-app/fi.metatavu.acgpanel.cotio/cotio.apk")
        runCommand("pm install $cotioApkPath")
        runCommand("pm grant fi.metatavu.acgpanel.cotio android.permission.READ_EXTERNAL_STORAGE")
    }

    /**
     * Show ACGPanel's settings activity and pause the installation
     */
    private fun startAcgPanelConfiguration() {
        val panelSettingsComponent = ComponentName.unflattenFromString(
            "fi.metatavu.acgpanel/.AcgPanelSettingsActivity"
        )
        val panelSettingsIntent = Intent(Intent.ACTION_VIEW)
        panelSettingsIntent.component = panelSettingsComponent
        startActivity(panelSettingsIntent)
        pause()
        incrementProgress()
    }

    /**
     * Show Cotio's settings activity and pause the installation
     */
    private fun startCotioConfiguration() {
        val cotioSettingsComponent = ComponentName.unflattenFromString(
            "fi.metatavu.acgpanel.cotio/.CotioSettingsActivity"
        )
        val cotioSettingsIntent = Intent(Intent.ACTION_VIEW)
        cotioSettingsIntent.component = cotioSettingsComponent
        startActivity(cotioSettingsIntent)
        pause()
        incrementProgress()
    }


    /**
     * Ensure that regular users can't access the system configuration
     * and remove other "android-isms"
     */
    private fun lockDownDevice() {
        // Configure app as device owner
        when (installTarget) {
            InstallTarget.ACGPanel -> {
                runCommand("dpm set-device-owner fi.metatavu.acgpanel/.DeviceAdminReceiver")
            }
            InstallTarget.Cotio -> {
                runCommand("dpm set-device-owner fi.metatavu.acgpanel.cotio/.DeviceAdminReceiver")
            }
        }
        // Disable other launchers so the user can't muck around
        runCommand("pm disable com.android.launcher3")
        runCommand("pm disable com.farmerbb.taskbar.androidx86")
        // Disable home key
        val genericKlPath = File(filesDir, "Generic.kl").absolutePath
        writeResource(R.raw.generic, genericKlPath)
        runCommand("cp $genericKlPath /system/usr/keylayout/Generic.kl")
        runCommand("chmod 644 /system/usr/keylayout/Generic.kl")
        // Disable Google Mobile Services (Chrome, Play etc)
        runCommand("pm disable com.google.android.gms")
        // Hide snackbars and toasts
        runCommand("appops set android TOAST_WINDOW deny")
        // Hide top and bottom bars
        runCommand("wm overscan 0,-48,0,-72")
    }

    /**
     * Install a keyboard with no emojis, autocomplete or other distractions
     */
    private fun installAndEnableSimpleKeyboard() {
        val simpleKeyboardApkPath = File(filesDir, "simplekeyboard.apk").absolutePath
        writeResource(R.raw.simplekeyboard, simpleKeyboardApkPath)
        runCommand("pm install $simpleKeyboardApkPath")
        runCommand("ime enable rkr.simplekeyboard.inputmethod/.latin.LatinIME")
        runCommand("ime disable com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime.VoiceInputMethodService")
        runCommand("ime disable com.android.inputmethod.latin/.LatinIME")
    }

    /**
     * Install the client logo as a custom boot animation
     */
    private fun installBootAnimation() {
        val bootAnimationPath = File(filesDir, "bootanimation.zip").absolutePath
        when (installTarget) {
            is InstallTarget.Cotio -> {
                writeResource(R.raw.cotioanimation, bootAnimationPath)
            }
            is InstallTarget.ACGPanel -> {
                writeResource(R.raw.bootanimation, bootAnimationPath)
            }
        }
        runCommand("cp $bootAnimationPath /system/media")
        runCommand("chmod 644 /system/media/bootanimation.zip")
        // Black background to remove awkward flash after boot animation
        WallpaperManager.getInstance(applicationContext)
            .setResource(R.raw.background)
        incrementProgress()
    }

    /**
     * Make a thread that runs the installation process
     */
    private fun makeProcessThread(): Thread = thread(start = false) {
        try {
            maxProgress = 31
            disableGrubMenuScreen()
            when (installTarget) {
                InstallTarget.ACGPanel -> {
                    installMainAppAsPrivileged()
                    startAcgPanelConfiguration()
                }
                InstallTarget.Cotio -> {
                    installCotioAppAsPrivileged()
                    startCotioConfiguration()
                }
            }
            lockDownDevice()
            installAndEnableSimpleKeyboard()
            installBootAnimation()
            completed()
        } catch (ex: Exception) {
            print("Exception: ${ex.javaClass.name}: ${ex.message}", error = true)
            completed(success = false)
        }
    }

    /**
     * The thread that runs the installation process. Non-null
     * when the installation is running, and null otherwise.
     */
    var processThread: Thread? = null

    /**
     * Show a selection dialog for choosing the app to install
     */
    private fun selectInstallTarget() {
        val radioGroup = RadioGroup(this)
        radioGroup.orientation = RadioGroup.VERTICAL
        radioGroup.setPadding(20, 20, 20, 20)
        val acgPanelButton = RadioButton(this)
        acgPanelButton.text = getString(R.string.acgpanel)
        radioGroup.addView(acgPanelButton)
        val cotioButton = RadioButton(this)
        cotioButton.text = getString(R.string.cotio)
        radioGroup.addView(cotioButton)
        AlertDialog.Builder(this)
            .setView(radioGroup)
            .setTitle(getString(R.string.select_install_target))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                if (acgPanelButton.isChecked) {
                    installTarget = InstallTarget.ACGPanel
                }
                if (cotioButton.isChecked) {
                    installTarget = InstallTarget.Cotio
                }
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        if (state == State.Ready) {
            selectInstallTarget()
        }
    }

    /**
     * The event handler for the multi button
     */
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
            State.Errored -> {
                finish()
            }
        }
    }
}
