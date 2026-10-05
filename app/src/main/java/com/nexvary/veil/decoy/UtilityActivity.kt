package com.nexvary.veil.decoy

import android.app.Activity
import android.os.Bundle
import android.text.InputType
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
        // Reject a stale private utility before it draws on return from the background.
        if(com.nexvary.veil.auth.VeilRuntime.session.current()!=utilityProfile) { finish(); return }
        handler.removeCallbacks(expire); handler.postDelayed(expire,1000)
    }
    override fun onPause() { handler.removeCallbacks(expire); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(expire); super.onDestroy() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        utilityProfile=com.nexvary.veil.auth.VeilRuntime.session.current()
        val root=VeilUi.root(this,utilityProfile!=com.nexvary.veil.core.VeilProfile.DECOY)
        root.addView(VeilUi.button(this,R.string.back) { finish() })
        when(intent.getStringExtra("utility")) {
            "notes" -> {
                root.addView(VeilUi.text(this,getString(R.string.notes),26f))
                root.addView(VeilUi.text(this,getString(R.string.note_info)))
                val prefs=getSharedPreferences("utility.notes."+utilityProfile.name,MODE_PRIVATE)
                val field=EditText(this).apply { id=R.id.note_input; isSaveEnabled=false; setHint(R.string.note_hint); setText(prefs.getString("text","")); maxLines=12 }
                root.addView(field,LinearLayout.LayoutParams(-1,0,1f))
                root.addView(VeilUi.button(this,R.string.save) { if(com.nexvary.veil.auth.VeilRuntime.session.current()==utilityProfile) prefs.edit().putString("text",field.text.toString().take(10_000)).apply() else finish() })
            }
            "clock" -> {
                root.addView(VeilUi.text(this,getString(R.string.clock),26f))
                root.addView(TextClock(this).apply { format24Hour="HH:mm:ss"; format12Hour="hh:mm:ss a"; textSize=40f })
                root.addView(TextClock(this).apply { format24Hour="EEE, d MMM yyyy"; format12Hour=format24Hour; textSize=22f })
            }
            else -> {
                root.addView(VeilUi.text(this,getString(R.string.calculator),26f))
                val field=EditText(this).apply { id=R.id.calculator_input; inputType=InputType.TYPE_CLASS_TEXT; isSingleLine=true }
                val result=VeilUi.text(this,"",28f).apply { id=R.id.calculator_result }
                root.addView(field); root.addView(result)
                root.addView(VeilUi.button(this,R.string.result) { result.text=try { CalculatorEngine.calculate(field.text.toString()).toString() } catch (_: Exception) { getString(R.string.calculation_error) } })
                root.addView(VeilUi.button(this,R.string.clear) { field.text.clear(); result.text="" })
                val keys=GridLayout(this).apply { columnCount=4 }
                listOf("7","8","9","/","4","5","6","*","1","2","3","-","0",".","+","=").forEach { key ->
                    keys.addView(Button(this).apply { text=key; layoutParams=GridLayout.LayoutParams().apply { width=0; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) }
                        setOnClickListener { if(key=="=") result.text=try { CalculatorEngine.calculate(field.text.toString()).toString() } catch (_: Exception) { getString(R.string.calculation_error) } else field.append(key) } })
                }; root.addView(keys)
            }
        }
    }
}
