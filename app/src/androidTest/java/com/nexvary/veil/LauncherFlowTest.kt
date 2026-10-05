package com.nexvary.veil

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import com.nexvary.veil.auth.PinProfileResolver
import com.nexvary.veil.core.VeilProfile
import com.nexvary.veil.decoy.UtilityActivity
import com.nexvary.veil.storage.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherFlowTest {
    private val context: Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    @Before fun configure() {
        // Only the disposable test emulator is controlled here; app code never dismisses Android's lock screen.
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")

        com.nexvary.veil.auth.VeilRuntime.session.lock()
        VeilStore(context).save(VeilConfig().apply { pins=listOf(PinProfileResolver.bind("246810".toCharArray(),VeilProfile.PRIVACY),PinProfileResolver.bind("135790".toCharArray(),VeilProfile.DECOY)) })
    }
    @Test fun startsConcealedSearchAndRecreationStayConcealed() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            onView(withId(R.id.app_search)).perform(typeText("zzzzzz"),closeSoftKeyboard())
            onView(withText(R.string.control)).check(doesNotExist())
            scenario.recreate()
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            onView(withText(R.string.control)).check(doesNotExist())
        }
    }
    @Test fun decoySettingsAndBackWork() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use {
            onView(withText(R.string.settings)).perform(click())
            onView(withText(R.string.brightness)).check(matches(isDisplayed()))
            onView(withText(R.string.control)).check(doesNotExist())
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
        }
    }
    @Test fun encryptedConfigRejectsTampering() {
        val file=java.io.File(context.filesDir,"veil-config.enc")
        val bytes=file.readBytes()
        Assert.assertFalse(String(bytes).contains("246810"))
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        Assert.assertThrows(Exception::class.java) { VeilStore(context).load() }
    }
    @Test fun calculatorWorksAndBackClosesUtility() {
        ActivityScenario.launch<UtilityActivity>(Intent(context,UtilityActivity::class.java).putExtra("utility","calculator")).use {
            onView(withId(R.id.calculator_input)).perform(typeText("2+3*4"),closeSoftKeyboard())
            onView(withText(R.string.result)).perform(click())
            onView(withId(R.id.calculator_result)).check(matches(withText("14.0")))
            onView(withText(R.string.back)).perform(click())
        }
    }
    private fun waitFor(scenario: ActivityScenario<VeilLauncherActivity>, predicate: (VeilLauncherActivity) -> Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+30_000
        var matched=false
        while(!matched && android.os.SystemClock.elapsedRealtime()<end) {
            scenario.onActivity { matched=predicate(it) }
            if(!matched) Thread.sleep(100)
        }
        Assert.assertTrue("Profile transition did not complete",matched)
    }
    @Test fun privacyAndDuressPinsSwitchActualLauncherAndRecreateRelocks() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { val views=arrayListOf<android.view.View>(); it.findViewById<android.view.View>(android.R.id.content).findViewsWithText(views,it.getString(R.string.control),android.view.View.FIND_VIEWS_WITH_TEXT); views.isNotEmpty() }
            onView(withText(R.string.control)).check(matches(isDisplayed()))
            onView(withText(R.string.control)).perform(click())
            onView(withText(R.string.profiles)).check(matches(isDisplayed()))
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("135790"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { val views=arrayListOf<android.view.View>(); it.findViewById<android.view.View>(android.R.id.content).findViewsWithText(views,it.getString(R.string.control),android.view.View.FIND_VIEWS_WITH_TEXT); views.isEmpty() && it.findViewById<android.view.View>(R.id.app_grid)!=null }
            onView(withText(R.string.control)).check(doesNotExist())
            onView(withText(R.string.settings)).perform(click())
            onView(withText(R.string.brightness)).check(matches(isDisplayed()))
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { val views=arrayListOf<android.view.View>(); it.findViewById<android.view.View>(android.R.id.content).findViewsWithText(views,it.getString(R.string.control),android.view.View.FIND_VIEWS_WITH_TEXT); views.isNotEmpty() }
            scenario.recreate()
            onView(withText(R.string.control)).check(doesNotExist())
        }
    }

    private fun capture(activity: android.app.Activity, name: String) {
        val view=activity.window.decorView
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File(activity.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        shell("mkdir -p /data/local/tmp/veil-ui-proof; cp "+file.absolutePath+" /data/local/tmp/veil-ui-proof/")
    }
    @Test fun decoyMappingRoutesToNotesAndHiddenIdentityIsNotSearchable() {
        val catalog=com.nexvary.veil.launcher.AppCatalog(context)
        val app=catalog.load(com.nexvary.veil.core.ProfilePolicy(VeilProfile.PRIVACY),unfiltered=true).first { it.packageName!="com.android.settings" }
        val config=VeilStore(context).load().apply {
            allowlists[VeilProfile.DECOY]=setOf(app.packageName)
            rules=listOf(com.nexvary.veil.core.DisguiseRule(app.identity,com.nexvary.veil.core.VeilPresentation.DECOY,"Daily Notes","notes",setOf(VeilProfile.DECOY)))
        }
        VeilStore(context).save(config)
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withText("Daily Notes")).perform(click())
            onView(withHint(R.string.note_hint)).check(matches(isDisplayed()))
            onView(withText(R.string.back)).perform(click())
            scenario.onActivity { capture(it,"decoy-grid-en") }
        }
        config.rules=config.rules.map { it.copy(presentation=com.nexvary.veil.core.VeilPresentation.HIDDEN) }
        VeilStore(context).save(config)
        Assert.assertTrue(catalog.load(config.policy(VeilProfile.DECOY)).none { it.identity==app.identity })
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.app_search)).perform(typeText("Daily Notes"),closeSoftKeyboard())
            scenario.onActivity { Assert.assertEquals(0,it.findViewById<android.widget.GridView>(R.id.app_grid).adapter.count) }
        }
    }
    @Test fun arabicUsesRtlAndRendersGrid() {
        val original=android.content.res.Configuration(context.resources.configuration)
        try {
            context.resources.updateConfiguration(android.content.res.Configuration(original).apply { setLocale(java.util.Locale("ar")) },context.resources.displayMetrics)
            ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val grid=activity.findViewById<android.view.View>(R.id.app_grid)
                    Assert.assertEquals(android.view.View.LAYOUT_DIRECTION_RTL,grid.layoutDirection)
                    capture(activity,"decoy-grid-ar")
                }
                onView(withText(R.string.settings)).perform(click())
                onView(withText(R.string.back)).perform(click())
                onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            }
        } finally { context.resources.updateConfiguration(original,context.resources.displayMetrics) }
    }

    @Test fun setupWizardPersistsDistinctPinsAndOpensProtectedCenter() {
        VeilStore(context).save(VeilConfig())
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withText(R.string.setup)).perform(click())
            onView(withId(R.id.private_pin_input)).perform(scrollTo(),typeText("246810"),closeSoftKeyboard())
            onView(withId(R.id.private_pin_confirm)).perform(scrollTo(),typeText("246810"),closeSoftKeyboard())
            onView(withId(R.id.decoy_pin_input)).perform(scrollTo(),typeText("135790"),closeSoftKeyboard())
            onView(withId(R.id.decoy_pin_confirm)).perform(scrollTo(),typeText("135790"),closeSoftKeyboard())
            onView(withId(R.id.pin_setup_save)).perform(scrollTo(),click())
            waitFor(scenario) { val views=arrayListOf<android.view.View>(); it.findViewById<android.view.View>(android.R.id.content).findViewsWithText(views,it.getString(R.string.profiles),android.view.View.FIND_VIEWS_WITH_TEXT); views.isNotEmpty() }
            val saved=VeilStore(context).load()
            Assert.assertEquals(VeilProfile.PRIVACY,PinProfileResolver(saved.pins).resolve("246810".toCharArray()))
            Assert.assertEquals(VeilProfile.DECOY,PinProfileResolver(saved.pins).resolve("135790".toCharArray()))
            scenario.onActivity { capture(it,"control-center-en") }
            onView(withText(R.string.back)).perform(click())
            onView(withText(R.string.lock)).perform(click())
            onView(withText(R.string.control)).check(doesNotExist())
        }
    }

    @Test fun failedPinBackoffSurvivesEncryptedReloadAndBlocksCorrectPin() {
        VeilStore(context).save(VeilStore(context).load().apply { failures=4 })
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("000000"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { VeilStore(context).load().failures==5 }
            Assert.assertTrue(VeilStore(context).load().blockedUntil>System.currentTimeMillis())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            onView(withText(R.string.control)).check(doesNotExist())
            Assert.assertEquals(VeilProfile.DECOY,com.nexvary.veil.auth.VeilRuntime.session.current())
        }
    }

}
