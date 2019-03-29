package fi.metatavu.acgpanel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.graphics.drawable.Icon
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import fi.metatavu.acgpanel.model.getServerSyncModel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

const val SERVER_SYNC_SERVICE_ID = 2
const val SERVER_SYNC_INTERVAL_MINUTES = "2"

class ServerSyncService : Service() {
    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val model = getServerSyncModel()

    private var running = false
    private var runningThread: Thread? = null

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(this)

    private fun process() {
        try {
            while (running) {
                val syncInterval = preferences
                    .getString(getString(R.string.pref_key_update_interval),
                               SERVER_SYNC_INTERVAL_MINUTES)
                val updateUrl = preferences
                    .getString(getString(R.string.pref_key_software_update_url),
                        SERVER_SYNC_INTERVAL_MINUTES)
                Log.d(javaClass.name, "Syncing with server...")
                model.serverSync()
                softwareUpdate(updateUrl)
                Thread.sleep((syncInterval.toLongOrNull() ?: 10) * 60L * 1000L)
            }
        } catch (ex: InterruptedException) {}
    }

    private fun softwareUpdate(updateUrl: String?) {
        try {
            val client = OkHttpClient.Builder().build()
            val versionResult = client
                .newCall(
                    Request.Builder().url("$updateUrl/version.txt").build())
                .execute()
            if (versionResult.isSuccessful) {
                val version = versionResult.body()!!.string().trim().toInt()
                val myVersion = packageManager
                    .getPackageInfo(packageName, 0)!!
                    .versionCode
                if (version > myVersion) {
                    val fileResult = client
                        .newCall(Request.Builder().url("$updateUrl/app.apk").build())
                        .execute()
                    if (fileResult.isSuccessful) {
                        val path = File(filesDir, "app.apk").absolutePath
                        fileResult.body()!!.byteStream().use {
                            Files.copy(it, Paths.get(path), StandardCopyOption.REPLACE_EXISTING)
                        }
                        Runtime.getRuntime()
                            .exec(
                                arrayOf(
                                    "su",
                                    "-c",
                                    "pm install -r $path"
                                )
                            )
                    } else {
                        Log.e(
                            javaClass.name, "Couldn't download software update:" +
                                    " ${fileResult.message()}"
                        )
                    }
                }
            } else {
                Log.e(javaClass.name, "Couldn't download version number: ${versionResult.message()}")
            }
        } catch (ex: Exception) {
            Log.e(javaClass.name, "Error when updating software: $ex")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (runningThread != null) {
            return Service.START_STICKY
        }
        Log.d(javaClass.name, "Starting sync service")
        running = true
        runningThread = thread(start = true) { process() }
        val channel = NotificationChannel(
            getString(R.string.app_name),
            getString(R.string.notifications_name),
            NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, channel.id)
            .setContentTitle(getString(R.string.server_sync_title))
            .setContentText(getString(R.string.server_sync_desc))
            .setSmallIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setLargeIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .build()
        startForeground(SERVER_SYNC_SERVICE_ID, notification)
        return Service.START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
