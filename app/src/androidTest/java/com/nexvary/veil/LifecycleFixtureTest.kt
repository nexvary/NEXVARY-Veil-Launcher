package com.nexvary.veil

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.veil.auth.PinProfileResolver
import com.nexvary.veil.core.*
import com.nexvary.veil.launcher.AppCatalog
import com.nexvary.veil.storage.*
import org.junit.*
import org.junit.runner.RunWith

/** Only scripts/verify_lifecycle.py invokes this fixture on disposable emulators. */
@RunWith(AndroidJUnit4::class)
class LifecycleFixtureTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val canary="Private Canary"
    private fun unlock(scenario: ActivityScenario<VeilLauncherActivity>) {
        onView(withId(R.id.home_clock)).perform(longClick())
        onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
        onView(withText(R.string.unlock)).perform(click())
        val deadline=android.os.SystemClock.elapsedRealtime()+30_000
        var authenticated=false
        while(!authenticated && android.os.SystemClock.elapsedRealtime()<deadline) {
            scenario.onActivity { authenticated=com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
            if(!authenticated) Thread.sleep(100)
        }
        Assert.assertEquals(VeilProfile.PRIVACY,com.nexvary.veil.auth.VeilRuntime.session.current())
        onView(withId(R.id.app_search)).perform(typeText(canary),closeSoftKeyboard())
        onView(org.hamcrest.Matchers.allOf(withText(canary),isDescendantOfA(withId(R.id.app_grid)))).check(matches(isDisplayed()))
        scenario.onActivity { Assert.assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0) }
    }
    @Test fun holdAuthenticatedSession() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("veil.lifecycle")=="true")
        if(InstrumentationRegistry.getArguments().getString("prepare")=="true") {
            val target=AppCatalog(context).load(ProfilePolicy(VeilProfile.PRIVACY),unfiltered=true).first { it.packageName=="com.android.settings" && !it.privateSpace }
            VeilStore(context).save(VeilConfig().apply {
                timeout=3_600_000L
                pins=listOf(PinProfileResolver.bind("246810".toCharArray(),VeilProfile.PRIVACY),PinProfileResolver.bind("135790".toCharArray(),VeilProfile.DECOY))
                rules=listOf(DisguiseRule(target.identity,VeilPresentation.DISGUISED,canary,"notes",setOf(VeilProfile.PRIVACY)))
            })
        }
        Assert.assertEquals(2,VeilStore(context).load().pins.size)
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            unlock(scenario)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("touch /data/local/tmp/veil-lifecycle-ready")).use { it.readBytes() }
            // The external harness must kill/reboot the authenticated process, not call session.lock().
            Thread.sleep(90_000)
            Assert.fail("Host did not terminate the authenticated fixture")
        }
    }
    @Test fun reauthenticateWithoutReplacingConfiguration() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("veil.lifecycle")=="true")
        Assert.assertEquals(2,VeilStore(context).load().pins.size)
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withText(canary)).check(doesNotExist())
            onView(withText(R.string.setup)).check(doesNotExist())
            unlock(scenario)
        }
    }
}
