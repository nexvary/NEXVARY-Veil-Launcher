package com.nexvary.veil.core

class VeilPolicyEngine {
    fun decide(identity: AppIdentity, profile: VeilProfile, rules: Collection<DisguiseRule>): VisibilityDecision {
        val rule = rules.firstOrNull { it.target == identity && profile in it.profiles }
            ?: return VisibilityDecision(true, true)
        return when (rule.presentation) {
            VeilPresentation.REAL -> VisibilityDecision(true, true)
            VeilPresentation.HIDDEN -> VisibilityDecision(false, false)
            VeilPresentation.DISGUISED -> VisibilityDecision(true, true, rule.decoyLabel, rule.decoyIconKey)
            VeilPresentation.DECOY -> VisibilityDecision(true, false, rule.decoyLabel, rule.decoyIconKey)
        }
    }
    fun filterForSearch(items: Collection<AppIdentity>, profile: VeilProfile, rules: Collection<DisguiseRule>) =
        items.filter { decide(it, profile, rules).visible }
}
