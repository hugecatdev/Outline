package com.wavecat.outline.api.locks.utils

import android.os.SystemClock
import android.util.Log

class StepsLock(
    private val locks: List<Lock>,
    private val interval: Long
) : Lock() {
    init {
        require(locks.isNotEmpty()) { "Steps requires at least one lock" }
    }

    private var index = 0

    private var stepTime = 0L

    override fun tryUnlock(event: Any): Boolean? {
        val currentTime = SystemClock.uptimeMillis()

        if (index > 0 && currentTime - stepTime > interval) {
            Log.i(TAG, "$this time reset")
            reset()
        }

        return when (locks[index].tryUnlock(event)) {
            true -> {
                if (index == locks.lastIndex) {
                    Log.i(TAG, "$this unlock")
                    reset()
                    true
                } else {
                    index++
                    stepTime = currentTime
                    Log.i(TAG, "$this waiting for step $index")
                    null
                }
            }

            false -> {
                if (index > 0)
                    Log.i(TAG, "$this chain reset")

                reset()
                false
            }

            null -> null
        }
    }

    override fun reset() {
        index = 0
        locks.forEach { it.reset() }
    }

    companion object {
        const val TAG = "StepsLock"
    }
}
