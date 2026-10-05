package com.nexvary.veil.decoy

import android.app.Activity
import android.app.AlertDialog
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.nexvary.veil.R
import com.nexvary.veil.VeilUi

class DecoySettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root=VeilUi.root(this,false)
        VeilUi.header(this,root,R.string.settings) { finish() }
        val content=VeilUi.scroll(this,root)
        val prefs=getSharedPreferences("utility.settings",MODE_PRIVATE)
        val colors=intArrayOf(0xff101822.toInt(),0xff132b30.toInt(),0xff242135.toInt())
        fun appearance() { root.setBackgroundColor(if(prefs.getBoolean("dark",true)) colors[prefs.getInt("appearance",0).coerceIn(0,2)] else 0xff364050.toInt()) }
        appearance()
        window.attributes=window.attributes.apply { screenBrightness=prefs.getInt("brightness",55).coerceAtLeast(5)/100f }
        content.addView(VeilUi.text(this,getString(R.string.local_settings_hint),13f).apply { setTextColor(VeilUi.muted) })
        fun section(title: Int)=VeilUi.panel(this).also { panel ->
            panel.addView(VeilUi.text(this,getString(title),18f).apply { typeface=android.graphics.Typeface.DEFAULT_BOLD });content.addView(panel)
        }
        val display=section(R.string.display)
        display.addView(VeilUi.text(this,getString(R.string.brightness),14f))
        display.addView(SeekBar(this).apply {
            max=100;progress=prefs.getInt("brightness",55);minimumHeight=VeilUi.dp(this@DecoySettingsActivity,48)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean) {
                    if(fromUser) { window.attributes=window.attributes.apply { screenBrightness=value.coerceAtLeast(5)/100f };prefs.edit().putInt("brightness",value).apply() }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        display.addView(Switch(this).apply { setText(R.string.dark);isChecked=prefs.getBoolean("dark",true);minimumHeight=VeilUi.dp(this@DecoySettingsActivity,48);setTextColor(VeilUi.silver)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("dark",checked).apply();appearance() }
        })
        val sound=section(R.string.sound)
        sound.addView(VeilUi.text(this,getString(R.string.volume),14f))
        val audio=getSystemService(AudioManager::class.java)
        sound.addView(SeekBar(this).apply {
            max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);progress=audio.getStreamVolume(AudioManager.STREAM_MUSIC);minimumHeight=VeilUi.dp(this@DecoySettingsActivity,48)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean) { if(fromUser) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0) } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        content.addView(VeilUi.button(this,R.string.wallpaper) {
            AlertDialog.Builder(this).setTitle(R.string.wallpaper).setSingleChoiceItems(arrayOf(getString(R.string.appearance_graphite),getString(R.string.appearance_ocean),getString(R.string.appearance_violet)),prefs.getInt("appearance",0)) { dialog,i ->
                prefs.edit().putInt("appearance",i).putBoolean("dark",true).apply();appearance();dialog.dismiss()
            }.setNegativeButton(R.string.cancel,null).show()
        })
        content.addView(VeilUi.button(this,R.string.language) {
            AlertDialog.Builder(this).setTitle(R.string.language).setItems(arrayOf(getString(R.string.english),getString(R.string.arabic))) { _,i ->
                val locales=android.os.LocaleList.forLanguageTags(if(i==0) "en" else "ar")
                if(android.os.Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales=locales
                else { resources.updateConfiguration(android.content.res.Configuration(resources.configuration).apply { setLocales(locales) },resources.displayMetrics);recreate() }
            }.show()
        })
        section(R.string.date_time).addView(TextClock(this).apply {
            format24Hour="EEE, d MMM yyyy  HH:mm";format12Hour="EEE, d MMM yyyy  hh:mm a";textSize=15f;setTextColor(VeilUi.muted);gravity=Gravity.START
        })
        section(R.string.about_phone).addView(VeilUi.text(this,getString(R.string.phone_details,android.os.Build.MANUFACTURER,android.os.Build.MODEL,android.os.Build.VERSION.RELEASE),15f).apply { setTextColor(VeilUi.muted) })
    }
}
