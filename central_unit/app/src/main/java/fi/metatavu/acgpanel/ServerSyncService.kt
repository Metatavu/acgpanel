package fi.metatavu.acgpanel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log
import fi.metatavu.acgpanel.model.getServerSyncModel
import kotlin.concurrent.thread

const val SERVER_SYNC_SERVICE_ID = 2
const val SERVER_SYNC_INTERVAL_MS = 2L*60L*1000L

class ServerSyncService : Service() {
    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val model = getServerSyncModel()

    private var running = false
    private var runningThread: Thread? = null

    private fun process() {
        try {
            while (running) {
                Log.d(javaClass.name, "Syncing with server...")
                model.serverSync()
                Thread.sleep(SERVER_SYNC_INTERVAL_MS)
            }
        } catch (ex: InterruptedException) {

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
