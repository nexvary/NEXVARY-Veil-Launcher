package com.nexvary.veil.auth

import com.nexvary.veil.core.VeilProfile
import org.junit.Assert.*
import org.junit.Test

class PinProfileResolverTest {
    @Test fun differentPinsResolveDifferentProfilesAndAreErased() {
        val privatePin="246810".toCharArray()
        val privacy=PinProfileResolver.bind(privatePin,VeilProfile.PRIVACY)
        assertTrue(privatePin.all { it=='\u0000' })
        val decoy=PinProfileResolver.bind("135790".toCharArray(),VeilProfile.DECOY)
        val resolver=PinProfileResolver(listOf(privacy,decoy))
        assertEquals(VeilProfile.PRIVACY,resolver.resolve("246810".toCharArray()))
        assertEquals(VeilProfile.DECOY,resolver.resolve("135790".toCharArray()))
        assertNull(resolver.resolve("000000".toCharArray()))
        assertFalse(privacy.salt.contentEquals(decoy.salt))
    }
    @Test fun samePinHasDifferentSaltAndHash() {
        val a=PinProfileResolver.bind("123456".toCharArray(),VeilProfile.PRIVACY)
        val b=PinProfileResolver.bind("123456".toCharArray(),VeilProfile.PRIVACY)
        assertFalse(a.digest.contentEquals(b.digest))
    }
    @Test fun sessionIsFailClosedAfterTimeoutAndRestart() {
        var time=10L
        val session=SessionGate { time }
        assertEquals(VeilProfile.DECOY,session.current())
        session.unlock(VeilProfile.PRIVACY,30_000)
        assertEquals(VeilProfile.PRIVACY,session.current())
        time+=30_000
        assertEquals(VeilProfile.DECOY,session.current())
        session.unlock(VeilProfile.NORMAL,30_000)
        session.lock()
        assertEquals(VeilProfile.DECOY,session.current())
        assertEquals(VeilProfile.DECOY,SessionGate { time }.current())
    }
}
