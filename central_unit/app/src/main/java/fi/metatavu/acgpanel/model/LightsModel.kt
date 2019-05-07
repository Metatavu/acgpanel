package fi.metatavu.acgpanel.model

data class LightsRequest(
    /**
     * Intensity of the lights, between 0 and 32 (*inclusive*)
     */
    val intensity: Int
)

abstract class LightsModel {

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun unSchedule(callback: Runnable)
    protected abstract val lightsTimeoutInSeconds: Int
    protected abstract val lightsLowIntensity: Int
    protected abstract val lightsHighIntensity: Int

    private val lightsRequestListeners = mutableListOf<(LightsRequest) -> Unit>()
    private var lightsHigh = false

    private val lightsTimeoutCallback = Runnable {
        if (lightsHigh) {
            setLightIntensity(lightsLowIntensity)
        }
        lightsHigh = false
    }

    fun addLightsRequestListener(listener: (LightsRequest) -> Unit) {
        lightsRequestListeners.add(listener)
    }

    fun removeLightsRequestListener(listener: (LightsRequest) -> Unit) {
        lightsRequestListeners.remove(listener)
    }

    fun refreshLights() {
        unSchedule(lightsTimeoutCallback)
        schedule(lightsTimeoutCallback, lightsTimeoutInSeconds * 1000L)
        if (!lightsHigh) {
            setLightIntensity(lightsHighIntensity)
        }
        lightsHigh = true
    }

    private fun setLightIntensity(intensity: Int) {
        if (lightsRequestListeners.size != 1) {
            throw IllegalStateException("Exactly one request listener must be present")
        }
        for (listener in lightsRequestListeners) {
            listener(LightsRequest(intensity))
        }
    }

}