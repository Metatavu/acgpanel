package fi.metatavu.acgpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StartMcuCommunicationServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val intent = Intent(context, McuCommunicationService::class.java)
        context.startService(intent)
    }
}
