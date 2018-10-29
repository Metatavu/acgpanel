package fi.metatavu.acgpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class Startup : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val startIntent = Intent(context, DefaultActivity::class.java)
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(startIntent)
    }
}
