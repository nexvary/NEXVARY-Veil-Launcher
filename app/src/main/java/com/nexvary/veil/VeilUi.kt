package com.nexvary.veil

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object VeilUi {
    fun root(activity: Activity): LinearLayout {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(17,20,25))
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left+dp(activity,16),safe.top+dp(activity,12),safe.right+dp(activity,16),safe.bottom+dp(activity,12)); insets
        }
        activity.setContentView(root)
        ViewCompat.requestApplyInsets(root)
        return root
    }
    fun dp(activity: Activity, value: Int) = (value*activity.resources.displayMetrics.density).toInt()
    fun text(activity: Activity, value: String, size: Float = 16f) = TextView(activity).apply {
        text=value; textSize=size; setTextColor(Color.rgb(221,228,237)); setPadding(0,12,0,12)
    }
    fun button(activity: Activity, id: Int, action: () -> Unit) = Button(activity).apply {
        setText(id); isAllCaps=false; setOnClickListener { action() }
    }
    fun card(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(30,35,44)); cornerRadius=22f; setStroke(1,Color.rgb(123,140,159))
    }
}
