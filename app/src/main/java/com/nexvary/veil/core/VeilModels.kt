package com.nexvary.veil.core

enum class VeilProfile { NORMAL, PRIVACY, DECOY }
enum class VeilPresentation { REAL, DISGUISED, HIDDEN, DECOY }

data class AppIdentity(val packageName: String, val className: String? = null, val userSerial: Long = 0)
data class DisguiseRule(
    val target: AppIdentity,
    val presentation: VeilPresentation,
    val decoyLabel: String? = null,
    val decoyIconKey: String? = null,
    val profiles: Set<VeilProfile> = setOf(VeilProfile.PRIVACY, VeilProfile.DECOY)
)
data class VisibilityDecision(
    val visible: Boolean,
    val launchRealTarget: Boolean,
    val labelOverride: String? = null,
    val iconOverrideKey: String? = null
)
