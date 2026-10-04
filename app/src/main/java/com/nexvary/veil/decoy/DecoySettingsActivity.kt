package com.nexvary.veil.decoy

import android.app.Activity
import android.os.Bundle
import android.widget.*
import com.nexvary.veil.R
import com.nexvary.veil.VeilUi

class DecoySettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root=VeilUi.root(this)
        root.addView(VeilUi.button(this,R.string.back) { finish() })
        root.addView(VeilUi.text(this,getString(R.string.settings),28f))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        val prefs=getSharedPreferences("utility.settings",MODE_PRIVATE)
        content.addView(VeilUi.text(this,getString(R.string.display),20f))
        content.addView(VeilUi.text(this,getString(R.string.brightness)))
        content.addView(SeekBar(this).apply {
            max=100; progress=prefs.getInt("brightness",55)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    window.attributes=window.attributes.apply { screenBrightness=value.coerceAtLeast(5)/100f }
                    prefs.edit().putInt("brightness",value).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        content.addView(Switch(this).apply { setText(R.string.dark); isChecked=prefs.getBoolean("dark",true)
            setOnCheckedChangeListener { _, checked -> root.setBackgroundColor(if(checked) 0xff111419.toInt() else 0xff364050.toInt()); prefs.edit().putBoolean("dark",checked).apply() } })
        content.addView(VeilUi.text(this,getString(R.string.sound),20f))
        content.addView(VeilUi.text(this,getString(R.string.volume)))
        val audio=getSystemService(android.media.AudioManager::class.java)
        content.addView(SeekBar(this).apply {
            max=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC); progress=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if(fromUser) runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,value,0) } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        content.addView(VeilUi.button(this,R.string.wallpaper) {
            val colors=intArrayOf(0xff111419.toInt(),0xff172c32.toInt(),0xff242135.toInt())
            android.app.AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.dark),getString(R.string.display),getString(R.string.wallpaper))) { _, i -> root.setBackgroundColor(colors[i]) }.show()
        })
        content.addView(VeilUi.button(this,R.string.language) { android.app.AlertDialog.Builder(this).setItems(arrayOf("English","العربية")) { _,i ->
            val locales=android.os.LocaleList.forLanguageTags(if(i==0) "en" else "ar")
            if(android.os.Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales=locales
            else { resources.updateConfiguration(android.content.res.Configuration(resources.configuration).apply { setLocales(locales) },resources.displayMetrics); recreate() }
        }.show() })
        content.addView(VeilUi.text(this,getString(R.string.date_time),20f))
        content.addView(TextClock(this).apply { format24Hour="EEE, d MMM yyyy  HH:mm"; format12Hour="EEE, d MMM yyyy  hh:mm a" })
        content.addView(VeilUi.text(this,getString(R.string.about_phone)+"\n"+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nAndroid "+android.os.Build.VERSION.RELEASE))
    }
}
