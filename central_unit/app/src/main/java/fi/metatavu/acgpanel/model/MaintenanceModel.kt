package fi.metatavu.acgpanel.model

abstract class MaintenanceModel {

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    abstract val maintenancePasscode: String
    abstract val adminPasscode: String?
    var isMaintenanceMode = false
    private var isDeviceErrorMode = false

    private val deviceErrorListeners: MutableList<(String) -> Unit> = mutableListOf()

    fun triggerDeviceError(message: String) {
        if (!isDeviceErrorMode) {
            isDeviceErrorMode = true
            schedule(Runnable{
                for (listener in deviceErrorListeners) {
                    listener(message)
                }
            }, 0)
        }
    }

    fun clearDeviceError() {
        isDeviceErrorMode = false
    }

    fun addDeviceErrorListener(listener: (String) -> Unit) {
        deviceErrorListeners.add(listener)
    }

    fun removeDeviceErrorListener(listener: (String) -> Unit) {
        deviceErrorListeners.remove(listener)
    }

}