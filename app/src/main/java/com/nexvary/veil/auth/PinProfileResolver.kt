package com.nexvary.veil.auth

import com.nexvary.veil.core.VeilProfile
import java.security.MessageDigest

data class PinProfileBinding(val digest: ByteArray, val profile: VeilProfile)

class PinProfileResolver(private val bindings: List<PinProfileBinding>) {
    fun resolve(pin: CharArray): VeilProfile? {
        val bytes = pin.concatToString().toByteArray(Charsets.UTF_8)
        pin.fill('\u0000')
        val candidate = MessageDigest.getInstance("SHA-256").digest(bytes)
        bytes.fill(0)
        return bindings.firstOrNull { MessageDigest.isEqual(it.digest, candidate) }?.profile
    }

    companion object {
        fun digest(pin: CharArray): ByteArray {
            val bytes = pin.concatToString().toByteArray(Charsets.UTF_8)
            pin.fill('\u0000')
            return MessageDigest.getInstance("SHA-256").digest(bytes).also { bytes.fill(0) }
        }
    }
}
