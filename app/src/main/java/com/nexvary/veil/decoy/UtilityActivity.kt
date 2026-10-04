package com.nexvary.veil.decoy

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import com.nexvary.veil.R
import com.nexvary.veil.VeilUi

class UtilityActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root=VeilUi.root(this)
        root.addView(VeilUi.button(this,R.string.back) { finish() })
        when(intent.getStringExtra("utility")) {
            "notes" -> {
                root.addView(VeilUi.text(this,getString(R.string.notes),26f))
                root.addView(VeilUi.text(this,getString(R.string.note_info)))
                val prefs=getSharedPreferences("utility.notes",MODE_PRIVATE)
                val field=EditText(this).apply { id=2001; setHint(R.string.note_hint); setText(prefs.getString("text","")); maxLines=12 }
                root.addView(field,LinearLayout.LayoutParams(-1,0,1f))
                root.addView(VeilUi.button(this,R.string.save) { prefs.edit().putString("text",field.text.toString().take(10_000)).apply() })
            }
            "clock" -> {
                root.addView(VeilUi.text(this,getString(R.string.clock),26f))
                root.addView(TextClock(this).apply { format24Hour="HH:mm:ss"; format12Hour="hh:mm:ss a"; textSize=40f })
                root.addView(TextClock(this).apply { format24Hour="EEE, d MMM yyyy"; format12Hour=format24Hour; textSize=22f })
            }
            else -> {
                root.addView(VeilUi.text(this,getString(R.string.calculator),26f))
                val field=EditText(this).apply { id=2002; inputType=InputType.TYPE_CLASS_TEXT; isSingleLine=true }
                val result=VeilUi.text(this,"",28f).apply { id=2003 }
                root.addView(field); root.addView(result)
                root.addView(VeilUi.button(this,R.string.result) { result.text=try { CalculatorEngine.calculate(field.text.toString()).toString() } catch (_: Exception) { getString(R.string.invalid) } })
                root.addView(VeilUi.button(this,R.string.clear) { field.text.clear(); result.text="" })
                val keys=GridLayout(this).apply { columnCount=4 }
                listOf("7","8","9","/","4","5","6","*","1","2","3","-","0",".","+","=").forEach { key ->
                    keys.addView(Button(this).apply { text=key; layoutParams=GridLayout.LayoutParams().apply { width=0; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) }
                        setOnClickListener { if(key=="=") result.text=try { CalculatorEngine.calculate(field.text.toString()).toString() } catch (_: Exception) { getString(R.string.invalid) } else field.append(key) } })
                }; root.addView(keys)
            }
        }
    }
}
