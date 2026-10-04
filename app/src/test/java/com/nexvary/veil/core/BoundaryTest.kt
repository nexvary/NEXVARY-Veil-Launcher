package com.nexvary.veil.core

import com.nexvary.veil.decoy.CalculatorEngine
import org.junit.Assert.*
import org.junit.Test

class BoundaryTest {
    @Test fun emptyEnforcedAllowlistDeniesAll() {
        val app=AppIdentity("secret","Main",9)
        assertFalse(ProfilePolicy(VeilProfile.DECOY,enforceAllowlist=true).allows(app))
        assertTrue(ProfilePolicy(VeilProfile.PRIVACY).allows(app))
    }
    @Test fun hiddenIsAbsentInSearchAndProfileRulesDoNotCross() {
        val app=AppIdentity("secret","Main",9)
        val rule=DisguiseRule(app,VeilPresentation.HIDDEN,profiles=setOf(VeilProfile.DECOY))
        val engine=VeilPolicyEngine()
        assertTrue(engine.filterForSearch(listOf(app),VeilProfile.DECOY,listOf(rule)).isEmpty())
        assertTrue(engine.decide(app,VeilProfile.PRIVACY,listOf(rule)).visible)
    }
    @Test fun decoyNeverLaunchesSensitiveTargetAndDisguiseDoes() {
        val app=AppIdentity("secret","Main",9)
        val engine=VeilPolicyEngine()
        val decoy=DisguiseRule(app,VeilPresentation.DECOY,"Notes","notes")
        assertFalse(engine.decide(app,VeilProfile.DECOY,listOf(decoy)).launchRealTarget)
        assertEquals("Notes",engine.decide(app,VeilProfile.DECOY,listOf(decoy)).labelOverride)
        assertTrue(engine.decide(app,VeilProfile.PRIVACY,listOf(decoy.copy(presentation=VeilPresentation.DISGUISED))).launchRealTarget)
        assertTrue(engine.decide(app.copy(userSerial=10),VeilProfile.DECOY,listOf(decoy)).launchRealTarget)
    }
    @Test fun calculatorHasOperatorPrecedenceAndSignedOperands() {
        assertEquals(14.0,CalculatorEngine.calculate("2+3*4"),0.0)
        assertEquals(-2.0,CalculatorEngine.calculate("6/-3"),0.0)
    }
    @Test(expected=IllegalArgumentException::class) fun calculatorRejectsInfiniteResult() { CalculatorEngine.calculate("1/0") }
}
