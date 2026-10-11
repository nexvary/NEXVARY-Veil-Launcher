package com.nexvary.veil.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.nexvary.veil.core.*
import com.nexvary.veil.privateSpace.PrivateSpaceBridge

data class LaunchableApp(val identity: AppIdentity, val label: String, val packageName: String,
    val component: ComponentName, val user: UserHandle, val icon: Drawable?, val decision: VisibilityDecision, val privateSpace: Boolean)

class AppCatalog(private val context: Context, private val policy: VeilPolicyEngine = VeilPolicyEngine()) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val privateSpace = PrivateSpaceBridge(context)
    fun observe(onChange: () -> Unit): LauncherApps.Callback {
        val callback=object: LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String,user: UserHandle) = onChange()
            override fun onPackageRemoved(packageName: String,user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String,user: UserHandle) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>,user: UserHandle,replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>,user: UserHandle,replacing: Boolean) = onChange()
            override fun onPackagesSuspended(packageNames: Array<out String>,user: UserHandle) = onChange()
            override fun onPackagesUnsuspended(packageNames: Array<out String>,user: UserHandle) = onChange()
        }
        launcher.registerCallback(callback,android.os.Handler(android.os.Looper.getMainLooper()))
        return callback
    }
    fun stopObserving(callback: LauncherApps.Callback) { launcher.unregisterCallback(callback) }
    private val settingsPackages: Set<String>
        get() = setOfNotNull("com.android.settings", context.packageManager.resolveActivity(
            Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName)
    fun load(profile: VeilProfile, rules: Collection<DisguiseRule>): List<LaunchableApp> = load(ProfilePolicy(profile,rules=rules.toList()))
    fun load(profile: ProfilePolicy, includePrivate: Boolean = false, unfiltered: Boolean = false): List<LaunchableApp> {
        val result = mutableListOf<LaunchableApp>()
        val profiles = try { launcher.profiles } catch (_: RuntimeException) { listOf(Process.myUserHandle()) }
        profiles.forEach { user ->
            val private = privateSpace.isPrivate(user)
            if (private && (!includePrivate || profile.profile != VeilProfile.PRIVACY)) return@forEach
            if (try { users.isQuietModeEnabled(user) || !users.isUserUnlocked(user) } catch (_: RuntimeException) { true }) return@forEach
            val activities = try { launcher.getActivityList(null,user) } catch (_: RuntimeException) { emptyList() }
            activities.forEach { info ->
                if (info.componentName.packageName == context.packageName) return@forEach
                val identity = AppIdentity(info.componentName.packageName,info.componentName.className,users.getSerialNumberForUser(user))
                val decision = if (unfiltered) VisibilityDecision(true,true) else policy.decide(identity,profile.profile,profile.rules)
                if (!unfiltered && (!decision.visible || !profile.allows(identity))) return@forEach
                // Settings in DECOY must route locally even if explicitly allowlisted.
                val effective = if (profile.settingsDecoy && identity.packageName in settingsPackages) decision.copy(launchRealTarget=false,iconOverrideKey="settings") else decision
                result += LaunchableApp(identity,effective.labelOverride ?: info.label.toString(),identity.packageName,info.componentName,user,
                    if(effective.iconOverrideKey != null || !effective.launchRealTarget) null else info.getBadgedIcon(context.resources.displayMetrics.densityDpi),effective,private)
            }
        }
        return result.sortedBy { it.label.lowercase() }
    }
    fun launch(app: LaunchableApp): Boolean {
        if (!app.decision.launchRealTarget) return false
        if (app.privateSpace && privateSpace.locked(app.user)) return false
        return try { launcher.startMainActivity(app.component,app.user,null,null); true } catch (_: RuntimeException) { false }
    }
}
