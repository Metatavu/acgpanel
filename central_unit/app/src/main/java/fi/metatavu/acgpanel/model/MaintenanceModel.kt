package fi.metatavu.acgpanel.model

abstract class MaintenanceModel {

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    abstract val maintenancePasscode: String
    var isMaintenanceMode = false
    var isDeviceErrorMode = false
        private set

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