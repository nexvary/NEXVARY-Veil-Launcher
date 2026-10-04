package com.nexvary.veil

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class VeilLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "NEXVARY Veil\nFoundation build"
            textSize = 24f
            setPadding(48, 96, 48, 48)
        })
    }
}
