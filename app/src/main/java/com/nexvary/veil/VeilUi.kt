package com.nexvary.veil

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object VeilUi {
    val silver=Color.rgb(230,235,243)
    val muted=Color.rgb(156,171,189)
    val accent=Color.rgb(106,220,201)
    fun root(activity: Activity, secure: Boolean = true): LinearLayout {
        if(secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xff111923.toInt(),0xff080d14.toInt()))
            layoutDirection=View.LAYOUT_DIRECTION_LOCALE
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left+dp(activity,20),safe.top+dp(activity,8),safe.right+dp(activity,20),safe.bottom+dp(activity,12)); insets
        }
        activity.window.statusBarColor=Color.TRANSPARENT
        activity.window.navigationBarColor=0xff080d14.toInt()
        activity.setContentView(root); ViewCompat.requestApplyInsets(root)
        return root
    }
    fun dp(activity: Activity,value: Int)=(value*activity.resources.displayMetrics.density).toInt()
    fun text(activity: Activity,value: String,size: Float=16f)=TextView(activity).apply {
        text=value; textSize=size; setTextColor(silver)
        setPadding(0,dp(activity,6),0,dp(activity,6))
        textDirection=View.TEXT_DIRECTION_LOCALE; textAlignment=View.TEXT_ALIGNMENT_GRAVITY
    }
    fun card(): GradientDrawable=GradientDrawable().apply {
        setColor(0xff1a2532.toInt()); cornerRadius=18f; setStroke(1,0xff344355.toInt())
    }
    fun panel(activity: Activity)=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL; background=card()
        val p=dp(activity,16);setPadding(p,p,p,p)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(activity,8);bottomMargin=dp(activity,8) }
    }
    fun button(activity: Activity,id: Int,action: () -> Unit)=Button(activity).apply {
        setText(id); isAllCaps=false; textSize=14f;setTextColor(silver)
        minimumHeight=dp(activity,48);setPadding(dp(activity,12),dp(activity,8),dp(activity,12),dp(activity,8))
        background=RippleDrawable(ColorStateList.valueOf(0x336adcc9),card(),null)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(activity,5);bottomMargin=dp(activity,5) }
        setOnClickListener { action() }
    }
    fun primary(activity: Activity,id: Int,action: () -> Unit)=button(activity,id,action).apply {
        setTextColor(0xff062821.toInt());typeface=Typeface.DEFAULT_BOLD
        background=RippleDrawable(ColorStateList.valueOf(0x33ffffff),GradientDrawable().apply { setColor(accent);cornerRadius=18f },null)
    }
    fun header(activity: Activity,root: LinearLayout,title: Int,back: () -> Unit) {
        val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(activity,56) }
        row.addView(button(activity,R.string.back,back).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_back,0,0,0)
            compoundDrawablePadding=dp(activity,4);background=null
        },LinearLayout.LayoutParams(-2,-2))
        row.addView(text(activity,activity.getString(title),21f).apply { typeface=Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(row)
    }
    fun input(activity: Activity,field: EditText) {
        field.setTextColor(silver);field.setHintTextColor(muted);field.background=card()
        field.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,12));field.minimumHeight=dp(activity,52)
        field.layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(activity,5);bottomMargin=dp(activity,5) }
    }
    fun icon(activity: Activity,drawable: Int,size: Int=48)=ImageView(activity).apply {
        setImageResource(drawable);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams=LinearLayout.LayoutParams(dp(activity,size),dp(activity,size))
    }
    fun info(activity: Activity,title: Int,body: Int,drawable: Int): LinearLayout=panel(activity).apply {
        val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        row.addView(icon(activity,drawable,36))
        row.addView(text(activity,activity.getString(title),17f).apply { typeface=Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(activity,12) })
        addView(row);addView(text(activity,activity.getString(body),14f).apply { setTextColor(muted) })
    }
    fun action(activity: Activity,title: Int,body: Int,drawable: Int,run: () -> Unit): LinearLayout=panel(activity).apply {
        addView(icon(activity,drawable,32))
        addView(text(activity,activity.getString(title),15f).apply { typeface=Typeface.DEFAULT_BOLD })
        addView(text(activity,activity.getString(body),12f).apply { setTextColor(muted) })
        minimumHeight=dp(activity,124);isClickable=true;isFocusable=true
        foreground=RippleDrawable(ColorStateList.valueOf(0x336adcc9),null,card())
        setOnClickListener { run() }
    }
    fun scroll(activity: Activity,root: LinearLayout): LinearLayout {
        val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(activity).apply { isFillViewport=true;addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        return content
    }
}
