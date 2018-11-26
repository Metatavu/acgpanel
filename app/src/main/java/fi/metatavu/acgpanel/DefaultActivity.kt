package fi.metatavu.acgpanel

import android.content.Intent
import android.os.Bundle
import android.view.View
import fi.metatavu.acgpanel.device.McuCommunicationService
import kotlinx.android.synthetic.main.activity_default.*

class DefaultActivity : PanelActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default)
        val mcuCommServiceIntent = Intent(this, McuCommunicationService::class.java)
        startService(mcuCommServiceIntent)
        val serverSyncServiceIntent = Intent(this, ServerSyncService::class.java)
        startService(serverSyncServiceIntent)
   }

    fun proceed(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, IdentifyActivity::class.java)
        startActivity(intent)
    }

    override val unlockButton: View
        get() = unlock_button
}
