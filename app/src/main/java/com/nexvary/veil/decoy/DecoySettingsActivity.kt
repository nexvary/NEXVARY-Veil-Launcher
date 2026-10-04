package com.nexvary.veil.decoy

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class DecoySettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 24)
        }
        root.addView(TextView(this).apply { text = "Settings"; textSize = 28f })
        root.addView(TextView(this).apply { text = "Display"; textSize = 18f })
        root.addView(TextView(this).apply { text = "Brightness" })
        root.addView(SeekBar(this).apply { max = 100; progress = 55 })
        root.addView(Switch(this).apply { text = "Dark theme"; isChecked = true })
        root.addView(TextView(this).apply { text = "Sound"; textSize = 18f })
        root.addView(TextView(this).apply { text = "Media volume" })
        root.addView(SeekBar(this).apply { max = 100; progress = 65 })
        root.addView(TextView(this).apply {
            text = "About phone\nAndroid " + android.os.Build.VERSION.RELEASE
            textSize = 16f
        })
        setContentView(root)
    }
}
