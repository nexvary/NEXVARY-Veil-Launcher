package com.nexvary.veil.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeilPolicyEngineTest {
    private val engine = VeilPolicyEngine()

    @Test fun privacyRuleRemovesProtectedItemFromSearch() {
        val item = AppIdentity("sample.app")
        val rules = listOf(DisguiseRule(item, VeilPresentation.HIDDEN))
        assertFalse(engine.decide(item, VeilProfile.PRIVACY, rules).visible)
        assertTrue(engine.filterForSearch(listOf(item), VeilProfile.PRIVACY, rules).isEmpty())
    }

    @Test fun decoyPresentationDoesNotLaunchProtectedTarget() {
        val item = AppIdentity("sample.app")
        val rules = listOf(DisguiseRule(item, VeilPresentation.DECOY, "Notes"))
        assertFalse(engine.decide(item, VeilProfile.DECOY, rules).launchRealTarget)
    }
}
