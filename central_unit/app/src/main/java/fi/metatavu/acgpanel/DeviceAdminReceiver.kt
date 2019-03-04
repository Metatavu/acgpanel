package fi.metatavu.acgpanel

import android.content.ComponentName
import android.content.Context
import android.app.admin.DeviceAdminReceiver as AndroidDeviceAdminReceiver

class DeviceAdminReceiver: AndroidDeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        fun getComponentName(context: Context) =
            ComponentName(context.applicationContext, DeviceAdminReceiver::class.java)
    }

}