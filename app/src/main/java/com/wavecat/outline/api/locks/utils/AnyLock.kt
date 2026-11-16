package com.wavecat.outline.api.locks.utils

class AnyLock(
    private val locks: List<Lock>,
) : Lock() {
    override fun tryUnlock(event: Any): Boolean? {
        var pending = false

        for (lock in locks) {
            when (lock.tryUnlock(event)) {
                true -> {
                    reset()
                    return true
                }

                null -> pending = true
                false -> {}
            }
        }

        if (pending)
            return null

        reset()
        return false
    }

    override fun reset() {
        locks.forEach { it.reset() }
    }
}
