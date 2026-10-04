package com.nexvary.veil.launcher

import android.content.Context
import android.content.Intent
import com.nexvary.veil.core.AppIdentity
import com.nexvary.veil.core.DisguiseRule
import com.nexvary.veil.core.VeilPolicyEngine
import com.nexvary.veil.core.VeilProfile

data class LaunchableApp(
    val identity: AppIdentity,
    val label: String,
    val packageName: String
)

class AppCatalog(private val context: Context, private val policy: VeilPolicyEngine = VeilPolicyEngine()) {
    fun load(profile: VeilProfile, rules: Collection<DisguiseRule>): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .map { info ->
                val identity = AppIdentity(info.activityInfo.packageName, info.activityInfo.name)
                val decision = policy.decide(identity, profile, rules)
                Triple(info, identity, decision)
            }
            .filter { it.third.visible }
            .map { (info, identity, decision) ->
                LaunchableApp(
                    identity,
                    decision.labelOverride ?: info.loadLabel(context.packageManager).toString(),
                    info.activityInfo.packageName
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(app: LaunchableApp): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
