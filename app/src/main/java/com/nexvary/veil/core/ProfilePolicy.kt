package com.nexvary.veil.core

enum class VeilSecurityTier { STANDARD, PRIVATE_SPACE, MANAGED }

data class VeilSession(
    val profile: VeilProfile,
    val tier: VeilSecurityTier,
    val duress: Boolean = false
)

data class ProfilePolicy(
    val profile: VeilProfile,
    val allowedPackages: Set<String> = emptySet(),
    val rules: List<DisguiseRule> = emptyList(),
    val settingsDecoy: Boolean = profile == VeilProfile.DECOY,
    val lockPrivateSpaceOnEntry: Boolean = profile == VeilProfile.DECOY,
    val enforceAllowlist: Boolean = false
) {
    fun allows(identity: AppIdentity): Boolean =
        (!enforceAllowlist && allowedPackages.isEmpty()) || identity.packageName in allowedPackages
}
