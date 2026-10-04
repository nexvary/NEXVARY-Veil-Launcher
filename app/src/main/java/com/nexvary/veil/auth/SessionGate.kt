package com.nexvary.veil.auth

import com.nexvary.veil.core.VeilProfile

class SessionGate(private val clock: () -> Long) {
    private var active = VeilProfile.DECOY
    private var deadline = 0L
    fun current(): VeilProfile {
        if (clock() >= deadline) lock()
        return active
    }
    fun unlock(profile: VeilProfile, timeoutMillis: Long) {
        require(timeoutMillis in 30_000..3_600_000)
        active = profile
        deadline = clock() + timeoutMillis
    }
    fun lock() { active = VeilProfile.DECOY; deadline = 0L }
}

object VeilRuntime {
    val session = SessionGate { android.os.SystemClock.elapsedRealtime() }
}
