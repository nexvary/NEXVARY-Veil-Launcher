package com.nexvary.veil.auth

import com.nexvary.veil.core.VeilProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinProfileResolverTest {
    @Test fun differentPinsResolveDifferentProfiles() {
        val privateDigest = PinProfileResolver.digest("2468".toCharArray())
        val decoyDigest = PinProfileResolver.digest("1357".toCharArray())
        val resolver = PinProfileResolver(listOf(
            PinProfileBinding(privateDigest, VeilProfile.PRIVACY),
            PinProfileBinding(decoyDigest, VeilProfile.DECOY)
        ))
        assertEquals(VeilProfile.PRIVACY, resolver.resolve("2468".toCharArray()))
        assertEquals(VeilProfile.DECOY, resolver.resolve("1357".toCharArray()))
        assertNull(resolver.resolve("0000".toCharArray()))
    }
}
