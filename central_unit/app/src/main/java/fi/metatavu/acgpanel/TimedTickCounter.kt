package fi.metatavu.acgpanel

import java.time.Duration
import java.time.Instant

class TimedTickCounter(
    private val ticksRequired: Int,
    private val tickTimeout: Duration,
    private val onTicksReached: () -> Unit
) {
    private var numTicks = 0
    private var lastTick: Instant? = null

    fun tick() {
        val now = Instant.now()
        if (lastTick?.isAfter(now.minus(tickTimeout)) == true) {
            numTicks++
            if (numTicks >= ticksRequired) {
                onTicksReached()
                numTicks = 1
            }
        } else {
            numTicks = 1
        }
        lastTick = now
    }

 }