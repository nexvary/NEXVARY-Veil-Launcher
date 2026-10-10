package com.nexvary.veil.decoy

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.nexvary.veil.R
import com.nexvary.veil.VeilUi

class UtilityActivity : Activity() {
    private var utilityProfile=com.nexvary.veil.core.VeilProfile.DECOY
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val expire=object: Runnable {
        override fun run() { if(com.nexvary.veil.auth.VeilRuntime.session.current()!=utilityProfile) finish() else handler.postDelayed(this,1000) }
    }
    override fun onResume() {
        super.onResume()
        if(com.nexvary.veil.auth.VeilRuntime.session.current()!=utilityProfile) { finish();return }
        handler.removeCallbacks(expire);handler.postDelayed(expire,1000)
    }
    override fun onPause() { handler.removeCallbacks(expire);super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(expire);super.onDestroy() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        utilityProfile=com.nexvary.veil.auth.VeilRuntime.session.current()
        val root=VeilUi.root(this,utilityProfile!=com.nexvary.veil.core.VeilProfile.DECOY)
        val key=intent.getStringExtra("utility")
        VeilUi.header(this,root,when(key) { "notes" -> R.string.notes;"clock" -> R.string.clock;else -> R.string.calculator }) { finish() }
        if(key=="notes") {
            root.addView(VeilUi.text(this,getString(R.string.note_info),12f).apply { setTextColor(VeilUi.muted) })
            val prefs=getSharedPreferences("utility.notes."+utilityProfile.name,MODE_PRIVATE)
            val field=EditText(this).apply {
                id=R.id.note_input;isSaveEnabled=false;setHint(R.string.note_hint);setText(prefs.getString("text",""))
                gravity=Gravity.TOP or Gravity.START;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                filters=arrayOf(android.text.InputFilter.LengthFilter(10_000));VeilUi.input(this@UtilityActivity,this)
            }
            root.addView(field,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=VeilUi.dp(this@UtilityActivity,8) })
            root.addView(VeilUi.primary(this,R.string.save) {
                if(com.nexvary.veil.auth.VeilRuntime.session.current()==utilityProfile) {
                    prefs.edit().putString("text",field.text.toString().take(10_000)).apply()
                    Toast.makeText(this,R.string.note_saved,Toast.LENGTH_SHORT).show()
                } else finish()
            })
            return
        }
        val content=VeilUi.scroll(this,root)
        if(key=="clock") {
            val face=VeilUi.panel(this)
            face.addView(VeilUi.text(this,getString(R.string.clock_hint),14f).apply { gravity=Gravity.CENTER;setTextColor(VeilUi.muted) })
            val time=TextClock(this).apply { format24Hour="HH:mm";format12Hour="hh:mm a";textSize=38f;setTextColor(VeilUi.silver);gravity=Gravity.CENTER;setPadding(0,VeilUi.dp(this@UtilityActivity,20),0,VeilUi.dp(this@UtilityActivity,12)) }
            face.addView(time,LinearLayout.LayoutParams(-1,-2))
            face.addView(TextClock(this).apply { format24Hour="EEEE, d MMM yyyy";format12Hour=format24Hour;textSize=16f;setTextColor(VeilUi.muted);gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,-2))
            face.addView(Switch(this).apply { id=R.id.clock_seconds_toggle;setText(R.string.clock_seconds);minimumHeight=VeilUi.dp(this@UtilityActivity,48)
                setOnCheckedChangeListener { _,checked -> time.format24Hour=if(checked) "HH:mm:ss" else "HH:mm";time.format12Hour=if(checked) "hh:mm:ss a" else "hh:mm a" } })
            content.addView(face)
            val stopwatch=VeilUi.panel(this)
            stopwatch.addView(VeilUi.text(this,getString(R.string.stopwatch),18f))
            val meter=Chronometer(this).apply { textSize=32f;setTextColor(VeilUi.accent);gravity=Gravity.CENTER;base=android.os.SystemClock.elapsedRealtime() }
            stopwatch.addView(meter,LinearLayout.LayoutParams(-1,-2))
            var running=false;var elapsed=0L
            val row=LinearLayout(this)
            val start=VeilUi.button(this,R.string.start_timer) {}
            start.setOnClickListener {
                if(running) { elapsed=android.os.SystemClock.elapsedRealtime()-meter.base;meter.stop();running=false;start.setText(R.string.start_timer) }
                else { meter.base=android.os.SystemClock.elapsedRealtime()-elapsed;meter.start();running=true;start.setText(R.string.pause_timer) }
            }
            row.addView(start,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(VeilUi.button(this,R.string.reset_timer) { meter.stop();elapsed=0;running=false;meter.base=android.os.SystemClock.elapsedRealtime();start.setText(R.string.start_timer) },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=VeilUi.dp(this@UtilityActivity,8) })
            stopwatch.addView(row);content.addView(stopwatch)
        } else {
            val display=VeilUi.panel(this)
            val field=EditText(this).apply { id=R.id.calculator_input;setHint(R.string.expression_hint);inputType=InputType.TYPE_CLASS_TEXT;isSingleLine=true;textDirection=View.TEXT_DIRECTION_LTR;VeilUi.input(this@UtilityActivity,this) }
            val result=VeilUi.text(this,"",30f).apply { id=R.id.calculator_result;gravity=Gravity.END;setTextColor(VeilUi.accent) }
            display.addView(field);display.addView(result,LinearLayout.LayoutParams(-1,-2))
            fun calculate() { result.text=try { CalculatorEngine.calculate(field.text.toString()).toString() } catch (_: Exception) { getString(R.string.calculation_error) } }
            val row=LinearLayout(this)
            row.addView(VeilUi.primary(this,R.string.result) { calculate() },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(VeilUi.button(this,R.string.clear) { field.text.clear();result.text="" },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=VeilUi.dp(this@UtilityActivity,8) })
            display.addView(row);content.addView(display)
            val keys=GridLayout(this).apply { columnCount=4;layoutDirection=View.LAYOUT_DIRECTION_LTR }
            resources.getStringArray(R.array.calculator_keys).forEach { key ->
                keys.addView(VeilUi.button(this,R.string.clear) {
                    if(key=="=") calculate() else field.append(when(key) { "×" -> "*";"÷" -> "/";"−" -> "-";else -> key })
                }.apply {
                    text=key;textSize=22f;setTextColor(if(key in listOf("×","÷","−","+","=")) VeilUi.accent else VeilUi.silver)
                    layoutParams=GridLayout.LayoutParams().apply { width=0;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(VeilUi.dp(this@UtilityActivity,3),VeilUi.dp(this@UtilityActivity,3),VeilUi.dp(this@UtilityActivity,3),VeilUi.dp(this@UtilityActivity,3)) }
                })
            }
            content.addView(keys)
            content.addView(VeilUi.button(this,R.string.delete_digit) { if(field.text.isNotEmpty()) field.text.delete(field.text.length-1,field.text.length) })
        }
    }
}
