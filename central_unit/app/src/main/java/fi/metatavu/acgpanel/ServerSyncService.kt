package fi.metatavu.acgpanel

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.graphics.drawable.Icon
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import fi.metatavu.acgpanel.model.getServerSyncModel
import net.schmizz.sshj.SSHClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
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


    private fun installPackage(packageName: String, packageData: InputStream) {
        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setAppPackageName(packageName)
        try {
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            val out = session.openWrite("$packageName.apk", 0, -1)
            packageData.copyTo(out)
            session.fsync(out)
            out.close()
            Log.d(javaClass.name, "installing package $packageName")
            session.commit(
                PendingIntent.getBroadcast(
                    this, sessionId,
                    Intent("android.intent.action.MAIN"), 0
                ).intentSender
            )
            Log.d(javaClass.name, "install intent sent")
        } catch (e: IOException) {
            Log.e(javaClass.name, "Error when installing package", e)
        }
    }

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

    @Suppress("unused")
    private fun syncSftpFolder(
        host: String,
        username: String,
        password: String,
        remotePath: String,
        localPath: String
    ) {
        val ssh = SSHClient()
        ssh.loadKnownHosts()
        ssh.connect(host)
        try {
            ssh.authPassword(username, password)
            val sftp = ssh.newSFTPClient()
            val fileNames = mutableListOf<String>()
            for (file in sftp.ls(remotePath)) {
                val localFile = File(localPath, file.name)
                if (!localFile.exists() ||
                        localFile.lastModified() <= file.attributes.mtime) {
                    sftp.get(file.path, localFile.absolutePath)
                }
                fileNames.add(file.name)
            }
            val localFiles = File(localPath).listFiles()
            for (file in localFiles) {
                if (!fileNames.contains(file.name)) {
                    file.delete()
                }
            }
        } finally {
            ssh.disconnect()
        }
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
                        fileResult.body()!!.byteStream().use {
                            installPackage("fi.metatavu.acgpanel", it)
                        }
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
