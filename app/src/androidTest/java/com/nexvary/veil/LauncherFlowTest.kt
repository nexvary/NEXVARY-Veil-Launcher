package com.nexvary.veil

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.*
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
    @Before fun configure() {
        VeilStore(context).save(VeilConfig().apply { pins=listOf(PinProfileResolver.bind("246810".toCharArray(),VeilProfile.PRIVACY),PinProfileResolver.bind("135790".toCharArray(),VeilProfile.DECOY)) })
    }
    @Test fun startsConcealedSearchAndRecreationStayConcealed() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use { scenario ->
            onView(withId(1003)).check(matches(isDisplayed()))
            onView(withId(1002)).perform(typeText("zzzzzz"),closeSoftKeyboard())
            onView(withText(R.string.control)).check(doesNotExist())
            scenario.recreate()
            onView(withId(1003)).check(matches(isDisplayed()))
            onView(withText(R.string.control)).check(doesNotExist())
        }
    }
    @Test fun decoySettingsAndBackWork() {
        ActivityScenario.launch<VeilLauncherActivity>(Intent(context,VeilLauncherActivity::class.java)).use {
            onView(withText(R.string.settings)).perform(click())
            onView(withText(R.string.brightness)).check(matches(isDisplayed()))
            onView(withText(R.string.control)).check(doesNotExist())
            onView(withText(R.string.back)).perform(click())
            onView(withId(1003)).check(matches(isDisplayed()))
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
            onView(withId(2002)).perform(typeText("2+3*4"),closeSoftKeyboard())
            onView(withText(R.string.result)).perform(click())
            onView(withId(2003)).check(matches(withText("14.0")))
            onView(withText(R.string.back)).perform(click())
        }
    }
}
