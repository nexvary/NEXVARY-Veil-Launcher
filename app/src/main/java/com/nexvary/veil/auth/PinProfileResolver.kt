package com.nexvary.veil.auth

import com.nexvary.veil.core.VeilProfile
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinProfileBinding(val digest: ByteArray, val profile: VeilProfile, val salt: ByteArray, val iterations: Int = 210_000)

class PinProfileResolver(private val bindings: List<PinProfileBinding>) {
    fun resolve(pin: CharArray): VeilProfile? {
        var result: VeilProfile? = null
        try {
            // Evaluate every binding; do not reveal the matched profile through an early return.
            bindings.forEach { binding ->
                val candidate = derive(pin, binding.salt, binding.iterations)
                if (MessageDigest.isEqual(binding.digest, candidate)) result = binding.profile
                candidate.fill(0)
            }
            return result
        } finally { pin.fill('\u0000') }
    }
    companion object {
        fun bind(pin: CharArray, profile: VeilProfile): PinProfileBinding {
            require(pin.size in 6..12 && pin.all { it in '0'..'9' })
            val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return try { PinProfileBinding(derive(pin, salt, 210_000), profile, salt) }
            finally { pin.fill('\u0000') }
        }
        private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
            require(iterations in 210_000..1_000_000 && salt.size == 32)
            val spec = PBEKeySpec(pin, salt, iterations, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        }
    }
}
