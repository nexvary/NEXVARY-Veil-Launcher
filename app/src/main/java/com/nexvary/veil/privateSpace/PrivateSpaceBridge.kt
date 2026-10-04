package com.nexvary.veil.privateSpace

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

class PrivateSpaceBridge(private val context: Context) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    fun available(): Boolean = Build.VERSION.SDK_INT >= 35 &&
        context.checkSelfPermission(Manifest.permission.ACCESS_HIDDEN_PROFILES) == PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    fun isPrivate(user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false
        // Unknown profile classification must not cause it to enter the main grid.
        return try { launcher.getLauncherUserInfo(user)?.let { it.userType == UserManager.USER_TYPE_PROFILE_PRIVATE } ?: (user != android.os.Process.myUserHandle()) }
        catch (_: SecurityException) { true }
    }
    fun profiles(): List<UserHandle> = if (available()) try { launcher.profiles.filter { isPrivate(it) } } catch (_: RuntimeException) { emptyList() } else emptyList()
    fun locked(user: UserHandle): Boolean = try { users.isQuietModeEnabled(user) || !users.isUserUnlocked(user) } catch (_: RuntimeException) { true }
    fun setLocked(user: UserHandle, locked: Boolean): Boolean = try {
        available() && users.requestQuietModeEnabled(locked,user)
    } catch (_: RuntimeException) { false }
    fun lockAll(): Boolean = profiles().map { setLocked(it,true) }.all { it }
}
