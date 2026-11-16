package com.wavecat.outline.api.locks.utils

import android.os.SystemClock

class CombinedLock(
    private val locks: List<Lock>,
    private val interval: Long
) : Lock() {
    init {
        require(locks.isNotEmpty()) { "Combined requires at least one lock" }
    }

    private val eventTimes = LongArray(locks.size) { NEVER }

    override fun tryUnlock(event: Any): Boolean? {
        val currentTime = SystemClock.uptimeMillis()

        locks.forEachIndexed { index, lock ->
            if (lock.tryUnlock(event) == true)
                eventTimes[index] = currentTime
        }

        if (eventTimes.all { it != NEVER && currentTime - it < interval }) {
            reset()
            return true
        }

        return null
    }

    override fun reset() {
        eventTimes.fill(NEVER)
        locks.forEach { it.reset() }
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
