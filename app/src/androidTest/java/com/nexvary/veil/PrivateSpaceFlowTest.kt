package com.nexvary.veil

import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexvary.veil.core.*
import com.nexvary.veil.launcher.AppCatalog
import com.nexvary.veil.privateSpace.PrivateSpaceBridge
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateSpaceFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(check: () -> Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        while(!check() && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
        Assert.assertTrue(check())
    }
    @Test fun realPrivateUserIsSeparateAndQuietModeExcludesItsApps() {
        Assume.assumeTrue(Build.VERSION.SDK_INT>=35)
        val created=shell("pm create-user --profileOf 0 --user-type android.os.usertype.profile.PRIVATE VeilTestSpace")
        val id=Regex("Success: created user id (\\d+)").find(created)?.groupValues?.get(1)?.toInt()
        Assume.assumeTrue("Emulator private profile unavailable: $created",id!=null)
        val userId=id!!
        try {
            shell("cmd role add-role-holder --user 0 android.app.role.HOME com.nexvary.veil")
            shell("pm install-existing --user $userId com.android.settings")
            shell("am start-user -w $userId")
            val bridge=PrivateSpaceBridge(context)
            Assert.assertTrue(bridge.available())
            val users=context.getSystemService(UserManager::class.java)
            await { bridge.profiles().any { users.getSerialNumberForUser(it)>=0 } }
            val user=bridge.profiles().first()
            if(bridge.locked(user)) bridge.setLocked(user,false)
            await { !bridge.locked(user) }
            val catalog=AppCatalog(context)
            val policy=ProfilePolicy(VeilProfile.PRIVACY)
            Assert.assertTrue(catalog.load(policy).none { it.privateSpace })
            Assert.assertTrue(catalog.load(policy,true).any { it.privateSpace && it.user==user })
            Assert.assertTrue(catalog.load(ProfilePolicy(VeilProfile.DECOY),true).none { it.privateSpace })
            Assert.assertTrue(bridge.setLocked(user,true))
            await { bridge.locked(user) }
            Assert.assertTrue(catalog.load(policy,true).none { it.user==user })
        } finally { shell("pm remove-user $userId") }
    }
}
