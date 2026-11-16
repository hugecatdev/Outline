package com.wavecat.outline.api.locks.utils

open class Lock {
    open fun tryUnlock(event: Any): Boolean? = null

    open fun reset() {}
}
