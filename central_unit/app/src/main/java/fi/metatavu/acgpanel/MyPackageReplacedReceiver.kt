package fi.metatavu.acgpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.preference.PreferenceManager
import fi.metatavu.acgpanel.device.McuCommunicationService

class MyPackageReplacedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: restart services
    }
}