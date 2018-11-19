package fi.metatavu.acgpanel.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StartMcuCommunicationServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val startIntent = Intent(context, McuCommunicationService::class.java)
        context.startService(startIntent)
    }
}
