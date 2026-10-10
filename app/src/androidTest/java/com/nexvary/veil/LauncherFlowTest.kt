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
    @Test fun drawerGestureButtonAndBackPreserveConcealment() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.open_drawer)).perform(click())
            scenario.onActivity { Assert.assertEquals(4,it.findViewById<android.widget.GridView>(R.id.app_grid).adapter.count);capture(it,"drawer-en") }
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.open_drawer)).check(matches(isDisplayed()))
            onView(withId(R.id.app_grid)).perform(swipeUp())
            onView(withText(R.string.back)).check(matches(isDisplayed()))
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.home_dock)).check(matches(isDisplayed()))
            scenario.onActivity { capture(it,"home-en") }
            onView(withText(R.string.control)).check(doesNotExist())
        }
    }
    @Test fun encryptedHomeOrderIsIndependentAndHiddenPinnedAppsStayExcluded() {
        val catalog=com.nexvary.veil.launcher.AppCatalog(context)
        val target=catalog.load(com.nexvary.veil.core.ProfilePolicy(VeilProfile.PRIVACY),unfiltered=true).first { !it.privateSpace }
        val key="app:${target.identity.packageName}|${target.identity.className.orEmpty()}|${target.identity.userSerial}"
        val cfg=VeilStore(context).load().apply {
            homeTiles[VeilProfile.DECOY]=listOf(key,"utility:clock","utility:notes")
            homeTiles[VeilProfile.NORMAL]=listOf("utility:notes")
            allowlists[VeilProfile.DECOY]=setOf(target.packageName)
            rules=listOf(com.nexvary.veil.core.DisguiseRule(target.identity,com.nexvary.veil.core.VeilPresentation.HIDDEN,profiles=setOf(VeilProfile.DECOY)))
        }
        VeilStore(context).save(cfg)
        val restored=VeilStore(context).load()
        Assert.assertEquals(cfg.homeTiles,restored.homeTiles)
        Assert.assertEquals(cfg.dockTiles,restored.dockTiles)
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            scenario.onActivity { Assert.assertEquals(2,it.findViewById<android.widget.GridView>(R.id.app_grid).adapter.count) }
            onView(withId(R.id.open_drawer)).perform(click())
            onView(withId(R.id.app_search)).perform(typeText("unlikely-hidden-query"),closeSoftKeyboard())
            scenario.onActivity { Assert.assertEquals(0,it.findViewById<android.widget.GridView>(R.id.app_grid).adapter.count) }
        }
    }
    @Test fun shortcutEditorPersistsExplicitChoiceAndOrder() {
        val target=com.nexvary.veil.launcher.AppCatalog(context)
            .load(com.nexvary.veil.core.ProfilePolicy(VeilProfile.PRIVACY),unfiltered=true)
            .groupBy { it.label }.values.first { it.size==1 && !it.single().privateSpace && it.single().packageName!="com.android.settings" }.single()
        // Explicit test-fixture approval; production never inserts real apps automatically.
        VeilStore(context).save(VeilStore(context).load().apply { allowlists[VeilProfile.DECOY]=setOf(target.packageName) })
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
            onView(withText(R.string.control)).perform(click())
            onView(withText(R.string.home_layout)).perform(scrollTo(),click())
            onView(withText(R.string.decoy)).perform(click())
            onView(withId(R.id.add_home_shortcut)).perform(scrollTo(),click())
            onView(withText(target.label)).perform(click())
            val key="app:${target.identity.packageName}|${target.identity.className.orEmpty()}|${target.identity.userSerial}"
            Assert.assertEquals(key,VeilStore(context).load().homeTiles[VeilProfile.DECOY]?.last())
            val label=target.label+"  ·  "+context.getString(R.string.edit_shortcut)
            onView(withText(label)).perform(scrollTo(),click())
            onView(withText(R.string.move_first)).perform(click())
            val saved=VeilStore(context).load()
            Assert.assertEquals(key,saved.homeTiles[VeilProfile.DECOY]?.first())
            Assert.assertEquals(listOf("utility:notes","utility:clock"),saved.homeTiles[VeilProfile.NORMAL])
            scenario.onActivity { capture(it,"home-editor-en") }
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.open_decoy)).perform(scrollTo(),click())
            onView(withText(target.label)).check(matches(isDisplayed()))
            scenario.onActivity { capture(it,"explicit-shortcuts-en") }
            onView(withText(R.string.control)).check(doesNotExist())
        }
    }
    @Test fun startsConcealedSearchAndRecreationStayConcealed() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            scenario.onActivity { Assert.assertFalse("Home must not open the search keyboard automatically",it.findViewById<android.widget.EditText>(R.id.app_search).hasFocus()) }
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
            captureForeground("settings-en")
            onView(withText(R.string.control)).check(doesNotExist())
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            onView(withText(R.string.settings)).perform(click())
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
        }
    }
    @Test fun encryptedConfigRejectsTampering() {
        val file=java.io.File(context.filesDir,"veil-config.enc")
        val bytes=file.readBytes()
        Assert.assertFalse(String(bytes).contains("246810"))
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        Assert.assertThrows(Exception::class.java) { VeilStore(context).load() }
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            scenario.onActivity { Assert.assertEquals(2,it.findViewById<android.widget.GridView>(R.id.app_grid).adapter.count) }
            onView(withText(R.string.control)).check(doesNotExist())
            scenario.recreate()
            scenario.onActivity { Assert.assertEquals(2,it.findViewById<android.widget.GridView>(R.id.app_grid).adapter.count) }
            Assert.assertArrayEquals(bytes,file.readBytes())
        }
    }
    @Test fun calculatorWorksAndBackClosesUtility() {
        ActivityScenario.launch<UtilityActivity>(Intent(context,UtilityActivity::class.java).putExtra("utility","calculator")).use {
            onView(withId(R.id.calculator_input)).perform(typeText("2+3*4"),closeSoftKeyboard())
            onView(withText(R.string.result)).perform(click())
            onView(withId(R.id.calculator_result)).check(matches(withText("14.0")))
            captureForeground("calculator-en")
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
    private fun waitForForeground(type: Class<out android.app.Activity>) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val end=android.os.SystemClock.elapsedRealtime()+30_000
        var matched=false
        while(!matched && android.os.SystemClock.elapsedRealtime()<end) {
            instrumentation.runOnMainSync {
                matched=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .any { type.isInstance(it) && it.hasWindowFocus() && !it.window.decorView.isLayoutRequested }
            }
            if(!matched) Thread.sleep(100)
        }
        Assert.assertTrue("Foreground activity transition did not complete",matched)
    }
    @Test fun privacyAndDuressPinsSwitchActualLauncherAndRecreateRelocks() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { val views=arrayListOf<android.view.View>(); it.findViewById<android.view.View>(android.R.id.content).findViewsWithText(views,it.getString(R.string.control),android.view.View.FIND_VIEWS_WITH_TEXT); views.isNotEmpty() }
            onView(withText(R.string.control)).check(matches(isDisplayed()))
            scenario.onActivity { capture(it,"private-grid-en") }
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

    @Test fun disguisedTileUsesAliasIconAndLaunchesRealExactTarget() {
        val catalog=com.nexvary.veil.launcher.AppCatalog(context)
        val target=catalog.load(com.nexvary.veil.core.ProfilePolicy(VeilProfile.PRIVACY),unfiltered=true)
            .first { it.packageName=="com.android.settings" && !it.privateSpace }
        VeilStore(context).save(VeilStore(context).load().apply {
            rules=listOf(com.nexvary.veil.core.DisguiseRule(target.identity,
                com.nexvary.veil.core.VeilPresentation.DISGUISED,"Desk Utility","clock",setOf(VeilProfile.PRIVACY)))
        })
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
            onView(withId(R.id.app_search)).perform(typeText("Desk Utility"),closeSoftKeyboard())
            val tile=catalog.load(VeilStore(context).load().policy(VeilProfile.PRIVACY)).single { it.label=="Desk Utility" }
            Assert.assertEquals(target.component,tile.component)
            Assert.assertEquals(target.user,tile.user)
            Assert.assertEquals("clock",tile.decision.iconOverrideKey)
            Assert.assertTrue(tile.decision.launchRealTarget)
            scenario.onActivity { capture(it,"disguised-tile-en") }
            onView(org.hamcrest.Matchers.allOf(withText("Desk Utility"),isDescendantOfA(withId(R.id.app_grid)))).perform(click())
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            val end=android.os.SystemClock.elapsedRealtime()+10_000
            while(automation.rootInActiveWindow?.packageName?.toString()!=target.packageName && android.os.SystemClock.elapsedRealtime()<end) Thread.sleep(100)
            Assert.assertEquals(target.packageName,automation.rootInActiveWindow?.packageName?.toString())
        }
    }

    private fun capture(activity: android.app.Activity, name: String) = captureView(activity.window.decorView,name)
    private fun captureView(view: android.view.View,name: String) {
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File(context.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        // Stream bytes through the shell pipe; scoped storage can deny shell reads of app files.
        shell("mkdir -p /data/local/tmp/veil-ui-proof")
        val destination="/data/local/tmp/veil-ui-proof/$name.png"
        val pipes=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("dd of=$destination")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { output -> file.inputStream().use { it.copyTo(output) } }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
        Assert.assertTrue("Screenshot must survive test package cleanup",shell("wc -c $destination").trim().startsWith(file.length().toString()))
    }
    private fun captureForeground(name: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            capture(activity,name)
        }
    }
    @Test fun clockSecondsStopwatchAndBackWork() {
        ActivityScenario.launch<UtilityActivity>(Intent(context,UtilityActivity::class.java).putExtra("utility","clock")).use { scenario ->
            onView(withId(R.id.clock_seconds_toggle)).perform(click())
            scenario.onActivity { activity ->
                fun clocks(view: android.view.View): List<android.widget.TextClock> = when(view) {
                    is android.widget.TextClock -> listOf(view)
                    is android.view.ViewGroup -> (0 until view.childCount).flatMap { clocks(view.getChildAt(it)) }
                    else -> emptyList()
                }
                Assert.assertEquals("HH:mm:ss",clocks(activity.window.decorView).first().format24Hour.toString())
                capture(activity,"clock-en")
            }
            onView(withText(R.string.start_timer)).perform(scrollTo(),click())
            onView(withText(R.string.pause_timer)).check(matches(isDisplayed())).perform(click())
            onView(withText(R.string.reset_timer)).perform(click())
            onView(withText(R.string.start_timer)).check(matches(isDisplayed()))
            onView(withText(R.string.back)).perform(click())
        }
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
            onView(withId(R.id.open_drawer)).perform(click())
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
        val localeManager=context.getSystemService(android.app.LocaleManager::class.java)
        val original=localeManager.applicationLocales
        try {
            localeManager.applicationLocales=android.os.LocaleList.forLanguageTags("ar")
            ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val grid=activity.findViewById<android.view.View>(R.id.app_grid)
                    Assert.assertEquals(android.view.View.LAYOUT_DIRECTION_RTL,grid.layoutDirection)
                    Assert.assertEquals("الإعدادات",activity.getString(R.string.settings))
                    Assert.assertEquals("ابحث عن تطبيق",activity.findViewById<android.widget.EditText>(R.id.app_search).hint.toString())
                    capture(activity,"decoy-grid-ar")
                }
                onView(withText(R.string.settings)).perform(click())
                captureForeground("settings-ar")
                onView(withText(R.string.back)).perform(click())
                onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            }
        } finally { localeManager.applicationLocales=original }
    }

    @Test fun landscapeLargeTextRendersAndRotationRelocks() {
        Assume.assumeTrue(shell("getprop ro.kernel.qemu").trim()=="1")
        val original=shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 1.5")
            ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
                onView(withId(R.id.home_clock)).perform(longClick())
                onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
                onView(withText(R.string.unlock)).perform(click())
                waitFor(scenario) { com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
                scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitFor(scenario) { it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE && com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.DECOY && it.findViewById<android.view.View>(R.id.app_grid)?.height?.let { height -> height>0 }==true }
                waitForForeground(VeilLauncherActivity::class.java)
                Assert.assertEquals(VeilProfile.DECOY,com.nexvary.veil.auth.VeilRuntime.session.current())
                onView(withText(R.string.control)).check(doesNotExist())
                onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
                scenario.onActivity {
                    val grid=it.findViewById<android.widget.GridView>(R.id.app_grid)
                    Assert.assertTrue(grid.height>=VeilUi.dp(it,48))
                    capture(it,"landscape-large-text")
                }
                onView(withText(R.string.settings)).perform(click())
                waitForForeground(com.nexvary.veil.decoy.DecoySettingsActivity::class.java)
                onView(withText(R.string.back)).perform(click())
                waitForForeground(VeilLauncherActivity::class.java)
                onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            }
        } finally {
            if(original.toFloatOrNull()!=null) shell("settings put system font_scale $original") else shell("settings delete system font_scale")
        }
    }

    @Test fun setupWizardPersistsDistinctPinsAndOpensProtectedCenter() {
        VeilStore(context).save(VeilConfig())
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            scenario.onActivity { capture(it,"welcome-en") }
            onView(withText(R.string.setup)).perform(click())
            scenario.onActivity { capture(it,"setup-private-en") }
            onView(withId(R.id.private_pin_input)).perform(scrollTo(),typeText("246810"),closeSoftKeyboard())
            onView(withId(R.id.private_pin_confirm)).perform(scrollTo(),typeText("246810"),closeSoftKeyboard())
            onView(withId(R.id.wizard_next)).perform(click())
            scenario.onActivity { capture(it,"setup-decoy-en") }
            onView(withId(R.id.decoy_pin_input)).perform(scrollTo(),typeText("135790"),closeSoftKeyboard())
            onView(withId(R.id.decoy_pin_confirm)).perform(scrollTo(),typeText("135790"),closeSoftKeyboard())
            onView(withId(R.id.wizard_next)).perform(click())
            scenario.onActivity { capture(it,"setup-ready-en") }
            onView(withId(R.id.pin_setup_save)).perform(click())
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

    @Test fun protectedGuideConfiguresDecoyAndLeavesNoPrivateEntryInEnglishAndArabic() {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=33)
        val localeManager=context.getSystemService(android.app.LocaleManager::class.java)
        val original=localeManager.applicationLocales
        try {
            listOf("en","ar").forEach { language ->
                localeManager.applicationLocales=android.os.LocaleList.forLanguageTags(language)
                ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
                    onView(withId(R.id.usage_guide)).check(doesNotExist())
                    onView(withId(R.id.home_clock)).perform(longClick())
                    onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
                    onView(withText(R.string.unlock)).perform(click())
                    waitFor(scenario) { com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
                    onView(withText(R.string.control)).perform(click())
                    scenario.onActivity { capture(it,"control-center-$language") }
                    onView(withId(R.id.usage_guide)).perform(click())
                    onView(withText(R.string.guide_switch_title)).check(matches(isDisplayed()))
                    scenario.onActivity {
                        Assert.assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
                        Assert.assertEquals(if(language=="ar") android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR,it.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).layoutDirection)
                        capture(it,"usage-guide-$language")
                    }
                    onView(withId(R.id.configure_decoy)).perform(scrollTo(),click())
                    onView(withId(R.id.management_search)).check(matches(isDisplayed()))
                    onView(withText(context.getString(R.string.configure_profile,context.getString(R.string.decoy)))).check(matches(isDisplayed()))
                    onView(withText(R.string.back)).perform(click())
                    onView(withId(R.id.usage_guide)).perform(click())
                    androidx.test.espresso.Espresso.pressBack()
                    scenario.onActivity {
                        val button=it.findViewById<android.view.View>(R.id.open_decoy)
                        val visible=android.graphics.Rect()
                        Assert.assertTrue("Alternative-home button must be visible after Back",button.getGlobalVisibleRect(visible))
                        Assert.assertTrue("Multiline button must fit its row",visible.height()>=button.height*0.9f)
                        capture(it,"control-return-$language")
                    }
                    onView(withId(R.id.open_decoy)).perform(scrollTo(),click())
                    Assert.assertEquals(VeilProfile.DECOY,com.nexvary.veil.auth.VeilRuntime.session.current())
                    onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
                    onView(withId(R.id.usage_guide)).check(doesNotExist())
                    onView(withText(R.string.guide_switch_title)).check(doesNotExist())
                    onView(withText(R.string.control)).check(doesNotExist())
                }
            }
        } finally { localeManager.applicationLocales=original }
    }

    @Test fun appManagementUiSavesDisguiseAndReturnsThroughCenter() {
        val target=com.nexvary.veil.launcher.AppCatalog(context).load(com.nexvary.veil.core.ProfilePolicy(VeilProfile.PRIVACY),unfiltered=true)
            .groupBy { it.label }.values.first { it.size==1 && !it.single().privateSpace && it.single().packageName!="com.android.settings" }.single()
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
            onView(withText(R.string.control)).perform(click())
            onView(withText(R.string.visibility)).perform(click())
            onView(withText(R.string.privacy)).perform(click())
            onView(withId(R.id.management_search)).perform(replaceText(target.label),closeSoftKeyboard())
            scenario.onActivity { capture(it,"app-management-en") }
            onView(org.hamcrest.Matchers.allOf(withText(target.label),org.hamcrest.Matchers.not(withId(R.id.management_search)))).perform(click())
            onView(withId(R.id.presentation_picker)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.equalTo(context.getString(R.string.disguised))).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click())
            onView(withHint(R.string.label)).perform(replaceText("Desk Utility"),closeSoftKeyboard())
            onView(withHint(R.string.label)).perform(object: androidx.test.espresso.ViewAction {
                override fun getConstraints()=isDisplayed()
                override fun getDescription()="Capture the rendered disguise dialog"
                override fun perform(controller: androidx.test.espresso.UiController,view: android.view.View) { captureView(view.rootView,"app-disguise-form-en") }
            })
            onView(withText(R.string.save)).perform(click())
            val rule=VeilStore(context).load().rules.first { it.target.packageName==target.packageName && VeilProfile.PRIVACY in it.profiles }
            Assert.assertEquals(com.nexvary.veil.core.VeilPresentation.DISGUISED,rule.presentation)
            Assert.assertEquals("Desk Utility",rule.decoyLabel)
            onView(withText(R.string.back)).perform(click())
            onView(withText(R.string.profiles)).check(matches(isDisplayed()))
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.app_search)).perform(replaceText("Desk Utility"),closeSoftKeyboard())
            onView(org.hamcrest.Matchers.allOf(withText("Desk Utility"),isDescendantOfA(withId(R.id.app_grid)))).check(matches(isDisplayed()))
        }
    }
    @Test fun notesPersistAndEmergencyDoesNotExposePrivateNotes() {
        context.getSharedPreferences("utility.notes.PRIVACY",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("utility.notes.DECOY",Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(R.id.home_clock)).perform(longClick())
            onView(withHint(R.string.pin)).perform(typeText("246810"),closeSoftKeyboard())
            onView(withText(R.string.unlock)).perform(click())
            waitFor(scenario) { com.nexvary.veil.auth.VeilRuntime.session.current()==VeilProfile.PRIVACY }
            onView(withText(R.string.notes)).perform(click())
            onView(withId(R.id.note_input)).perform(replaceText("Disposable private test note"),closeSoftKeyboard())
            onView(withText(R.string.save)).perform(click())
            androidx.test.espresso.Espresso.pressBack()
            onView(withText(R.string.notes)).perform(click())
            onView(withId(R.id.note_input)).check(matches(withText("Disposable private test note")))
            onView(withText(R.string.back)).perform(click())
            onView(withId(R.id.app_search)).perform(longClick())
            onView(withText(R.string.control)).check(doesNotExist())
            Assert.assertEquals(VeilProfile.DECOY,com.nexvary.veil.auth.VeilRuntime.session.current())
            onView(withText(R.string.notes)).perform(click())
            onView(withId(R.id.note_input)).check(matches(withText(""))).perform(closeSoftKeyboard())
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
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
