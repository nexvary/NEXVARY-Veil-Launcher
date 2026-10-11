package com.nexvary.veil.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePolicyTest {
    @Test fun decoyDefaultsToSettingsDecoyAndPrivateLockIntent() {
        val policy = ProfilePolicy(VeilProfile.DECOY)
        assertTrue(policy.settingsDecoy)
        assertTrue(policy.lockPrivateSpaceOnEntry)
    }

    @Test fun allowlistRestrictsUnexpectedPackages() {
        val policy = ProfilePolicy(VeilProfile.DECOY, allowedPackages = setOf("com.example.safe"))
        assertTrue(policy.allows(AppIdentity("com.example.safe")))
        assertFalse(policy.allows(AppIdentity("com.example.secret")))
    }
}
