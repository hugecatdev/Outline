package com.wavecat.outline.api.locks.utils

class AllLock(
    private val locks: List<Lock>,
) : Lock() {
    override fun tryUnlock(event: Any): Boolean? {
        var pending = false

        for (lock in locks) {
            when (lock.tryUnlock(event)) {
                false -> {
                    reset()
                    return false
                }

                null -> pending = true
                true -> {}
            }
        }

        if (pending)
            return null

        reset()
        return true
    }

    override fun reset() {
        locks.forEach { it.reset() }
    }
}
