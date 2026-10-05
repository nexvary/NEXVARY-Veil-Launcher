package com.nexvary.veil

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.*
import android.os.*
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.nexvary.veil.auth.*
import com.nexvary.veil.core.*
import com.nexvary.veil.decoy.*
import com.nexvary.veil.launcher.*
import com.nexvary.veil.privateSpace.PrivateSpaceBridge
import com.nexvary.veil.storage.*
import java.util.concurrent.Executors

class VeilLauncherActivity : androidx.activity.ComponentActivity() {
    private val catalog by lazy { AppCatalog(this) }
    private val space by lazy { PrivateSpaceBridge(this) }
    private val store by lazy { VeilStore(this) }
    private val session = VeilRuntime.session
    private var config = VeilConfig()
    private var storageFailed=false
    private var privateLockConfirmed: Boolean?=null
    private var page="home"
    private var renderedProfile=VeilProfile.DECOY
    private var authBusy=false
    private var backAction: (() -> Unit)?=null
    private var wizardFields=listOf<EditText>()
    private var generation=0
    private val worker=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private var dialog: AlertDialog?=null
    private var catalogCallback: android.content.pm.LauncherApps.Callback?=null
    private val expiry=object: Runnable {
        override fun run() {
            if(session.current()!=renderedProfile) emergency()
            handler.postDelayed(this,1000)
        }
    }
    private val profileReceiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if(intent?.action==Intent.ACTION_SCREEN_OFF) emergency() else if(page=="home") showLauncher() }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        session.lock()
        onBackPressedDispatcher.addCallback(this, object: androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { backAction?.invoke() ?: showLauncher() } })
        try { config=store.load() } catch (_: Exception) { storageFailed=true }
        val filter=IntentFilter().apply {
            if(Build.VERSION.SDK_INT>=35) { addAction(Intent.ACTION_PROFILE_AVAILABLE); addAction(Intent.ACTION_PROFILE_UNAVAILABLE) }
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(profileReceiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(profileReceiver,filter)
        privateLockConfirmed=space.lockAll()
        catalogCallback=catalog.observe { if(!isDestroyed && page=="home") showLauncher() }
        showLauncher()
    }
    override fun onResume() {
        super.onResume()
        if(session.current()==VeilProfile.DECOY && renderedProfile!=VeilProfile.DECOY) emergency()
        else if(session.current()==VeilProfile.DECOY && page!="home" && config.pins.isNotEmpty()) showLauncher()
        else if(page=="home") showLauncher()
        handler.removeCallbacks(expiry); handler.postDelayed(expiry,1000)
    }
    override fun onPause() {
        super.onPause(); handler.removeCallbacks(expiry)
        if(isChangingConfigurations) session.lock()
    }
    override fun onDestroy() {
        catalogCallback?.let(catalog::stopObserving)
        generation++; wizardFields.forEach { it.text.clear() }; wizardFields=emptyList(); backAction=null; dialog?.dismiss(); handler.removeCallbacksAndMessages(null); worker.shutdownNow()
        unregisterReceiver(profileReceiver); super.onDestroy()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); showLauncher() }
    private fun toast(id: Int) { Toast.makeText(this,id,Toast.LENGTH_LONG).show() }
    private fun save(): Boolean = try { store.save(config); true } catch (_: Exception) { storageFailed=true; emergency(); toast(R.string.storage_error); false }
    private fun utility(key: String) { startActivity(Intent(this,UtilityActivity::class.java).putExtra("utility",key).putExtra("profile",session.current().name)) }
    private fun settings() {
        if(session.current()==VeilProfile.PRIVACY && !storageFailed) showControl()
        else startActivity(Intent(this,DecoySettingsActivity::class.java))
    }
    private fun emergency() {
        generation++; dialog?.dismiss(); session.lock()
        privateLockConfirmed=space.lockAll()
        showLauncher()
        // Unconfirmed platform lock is reported only inside the authenticated center.
    }
    private fun showLauncher() {
        page="home"; backAction=null
        wizardFields.forEach { it.text.clear() }; wizardFields=emptyList()
        val profile=session.current()
        renderedProfile=profile
        if(config.pins.isEmpty() && !storageFailed) { showWelcome(); return }
        val root=VeilUi.root(this,profile!=VeilProfile.DECOY)
        val clock=TextClock(this).apply {
            format24Hour="HH:mm"; format12Hour="hh:mm a"; textSize=if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE) 24f else 34f; gravity=Gravity.CENTER; setTextColor(VeilUi.silver); id=R.id.home_clock
            setOnLongClickListener { if(!storageFailed) { if(config.pins.isEmpty()) showSetup() else showPin() }; true }
        }
        val search=EditText(this).apply { VeilUi.input(this@VeilLauncherActivity,this); id=R.id.app_search; isSaveEnabled=false; setHint(R.string.search); isSingleLine=true
            setOnLongClickListener { if(config.emergencyEnabled) emergency(); true } }
        if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            root.addView(LinearLayout(this).apply {
                gravity=Gravity.CENTER_VERTICAL
                addView(clock,LinearLayout.LayoutParams(-2,-2))
                addView(search,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=VeilUi.dp(this@VeilLauncherActivity,12) })
            })
        } else {
            root.addView(clock)
            root.addView(TextClock(this).apply { format24Hour="EEE, d MMM"; format12Hour=format24Hour; textSize=14f; setTextColor(VeilUi.muted); gravity=Gravity.CENTER; setPadding(0,0,0,VeilUi.dp(this@VeilLauncherActivity,14)) })
            root.addView(search)
            root.addView(VeilUi.text(this,getString(R.string.apps),14f).apply { setTextColor(VeilUi.muted) })
        }
        if(storageFailed) root.addView(VeilUi.text(this,getString(R.string.storage_error)))
        val empty=VeilUi.text(this,getString(R.string.no_results),15f).apply { gravity=Gravity.CENTER;visibility=View.GONE }
        root.addView(empty)
        val grid=GridView(this).apply {
            id=R.id.app_grid; columnWidth=tileColumnWidth(); numColumns=GridView.AUTO_FIT
            stretchMode=GridView.STRETCH_COLUMN_WIDTH; verticalSpacing=VeilUi.dp(this@VeilLauncherActivity,8)
            horizontalSpacing=VeilUi.dp(this@VeilLauncherActivity,4)
        }
        root.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        // Filter one policy-checked snapshot while typing. Resume/profile broadcasts rebuild it.
        val snapshot=if(storageFailed) emptyList() else catalog.load(config.policy(profile))
        fun render(query: String) {
            val current=session.current()
            if(current!=profile) { emergency(); return }
            val apps=snapshot
            val entries=apps.filter { it.label.contains(query,true) }.map { Tile(it.label,it) }.toMutableList()
            listOf(R.string.calculator to "calculator",R.string.notes to "notes",R.string.clock to "clock",R.string.settings to "settings").forEach { (id,key) ->
                val label=getString(id)
                // A configured app/decoy with this label already provides the tile.
                // Avoid duplicate Settings/Calculator/Clock entries in a convincing profile.
                if(label.contains(query,true) && entries.none { it.label.equals(label,true) }) entries+=Tile(label,utility=key)
            }
            grid.adapter=TileAdapter(entries)
            empty.visibility=if(entries.isEmpty()) View.VISIBLE else View.GONE
            grid.setOnItemClickListener { _,_,position,_ ->
                val tile=entries[position]
                if(tile.app!=null) {
                    // Re-resolve against the current policy at click time to close stale-view races.
                    val clickProfile=session.current()
                    if(clickProfile!=profile) { emergency(); return@setOnItemClickListener }
                    val fresh=catalog.load(config.policy(clickProfile)).firstOrNull { it.identity==tile.app.identity }
                    if(session.current()!=clickProfile) { emergency(); return@setOnItemClickListener }
                    if(fresh!=null) {
                        if(!fresh.decision.launchRealTarget) {
                            val key=fresh.decision.iconOverrideKey ?: "calculator"
                            if(key=="settings") settings() else utility(key)
                        } else if(!catalog.launch(fresh)) toast(R.string.launch_failed)
                    } else showLauncher()
                } else if(tile.utility=="settings") settings() else utility(tile.utility ?: "calculator")
            }
        }
        render(""); search.addTextChangedListener(SimpleTextWatcher { render(it) })
        if(profile==VeilProfile.PRIVACY && !config.hidePrivate && !storageFailed) {
            val privateProfiles=space.profiles()
            if(privateProfiles.isNotEmpty()) {
                root.addView(VeilUi.text(this,getString(R.string.private_space),18f))
                privateProfiles.forEach { user ->
                    val locked=space.locked(user)
                    root.addView(VeilUi.button(this,if(locked) R.string.private_unlock else R.string.private_lock) {
                        if(!space.setLocked(user,!locked)) toast(R.string.private_pending)
                        showLauncher()
                    })
                }
                val privateGrid=GridView(this).apply {
                    columnWidth=tileColumnWidth(); numColumns=GridView.AUTO_FIT; stretchMode=GridView.STRETCH_COLUMN_WIDTH
                    horizontalSpacing=VeilUi.dp(this@VeilLauncherActivity,4); verticalSpacing=VeilUi.dp(this@VeilLauncherActivity,8)
                }
                val items=catalog.load(config.policy(profile),includePrivate=true).filter { it.privateSpace }.map { Tile(it.label,it) }
                privateGrid.adapter=TileAdapter(items)
                privateGrid.setOnItemClickListener { _,_,position,_ ->
                    if(session.current()!=VeilProfile.PRIVACY) { emergency(); return@setOnItemClickListener }
                    val app=catalog.load(config.policy(VeilProfile.PRIVACY),true).firstOrNull { it.identity==items[position].app?.identity }
                    if(session.current()!=VeilProfile.PRIVACY) { emergency(); return@setOnItemClickListener }
                    if(app!=null && app.decision.launchRealTarget) catalog.launch(app)
                    else if(app!=null) utility(app.decision.iconOverrideKey ?: "calculator")
                }
                if(items.isNotEmpty()) root.addView(privateGrid,LinearLayout.LayoutParams(-1,0,1f))
            }
        }
        if(profile==VeilProfile.PRIVACY) {
            val row=LinearLayout(this)
            row.addView(VeilUi.button(this,R.string.control) { showControl() },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(VeilUi.button(this,R.string.lock) { emergency() },LinearLayout.LayoutParams(0,-2,1f)); root.addView(row)
        }
    }
    private data class Tile(val label: String,val app: LaunchableApp?=null,val utility: String?=null)
    private fun tileColumnWidth() = VeilUi.dp(this,(66*resources.configuration.fontScale.coerceAtLeast(1f)).toInt())
    private inner class TileAdapter(private val items: List<Tile>): BaseAdapter() {
        override fun getCount()=items.size
        override fun getItem(position: Int)=items[position]
        override fun getItemId(position: Int)=position.toLong()
        override fun getView(position: Int, convert: View?, parent: ViewGroup?): View {
            val tile=items[position]
            return LinearLayout(this@VeilLauncherActivity).apply {
                orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
                foreground=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x336adcc9),null,VeilUi.card())
                setPadding(VeilUi.dp(this@VeilLauncherActivity,3),VeilUi.dp(this@VeilLauncherActivity,12),VeilUi.dp(this@VeilLauncherActivity,3),VeilUi.dp(this@VeilLauncherActivity,8)); minimumHeight=VeilUi.dp(this@VeilLauncherActivity,104)
                addView(ImageView(this@VeilLauncherActivity).apply {
                    if(tile.app?.icon!=null) setImageDrawable(tile.app.icon)
                    else setImageResource(when(tile.utility ?: tile.app?.decision?.iconOverrideKey) {
                        "notes" -> R.drawable.ic_notes; "clock" -> R.drawable.ic_clock; "settings" -> R.drawable.ic_settings; else -> R.drawable.ic_calculator
                    })
                },LinearLayout.LayoutParams(VeilUi.dp(this@VeilLauncherActivity,52),VeilUi.dp(this@VeilLauncherActivity,52)))
                addView(VeilUi.text(this@VeilLauncherActivity,tile.label,12.5f).apply {
                    gravity=Gravity.CENTER; minLines=2; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
                    setPadding(0,VeilUi.dp(this@VeilLauncherActivity,8),0,0)
                })
            }
        }
    }
    private fun pinField(hint: Int) = EditText(this).apply {
        VeilUi.input(this@VeilLauncherActivity,this); setHint(hint); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        isSingleLine=true; imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING; setSaveEnabled(false); importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        filters=arrayOf(android.text.InputFilter.LengthFilter(12))
    }
    private fun showPin() {
        if(authBusy) return
        val input=pinField(R.string.pin)
        val alert=AlertDialog.Builder(this).setView(input).setPositiveButton(R.string.unlock,null).setNegativeButton(R.string.cancel) { _,_ -> generation++ }.create()
        dialog=alert; alert.setOnCancelListener { generation++ }; alert.show(); alert.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(authBusy) return@setOnClickListener
            val pin=input.text.toString().toCharArray(); input.text.clear()
            if(System.currentTimeMillis()<config.blockedUntil) { pin.fill('\u0000'); toast(R.string.invalid); return@setOnClickListener }
            authBusy=true; alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            val token=generation
            worker.execute {
                val resolved=try { PinProfileResolver(config.pins).resolve(pin) } catch (_: Exception) { null }
                runOnUiThread {
                    authBusy=false
                    if(isDestroyed || token!=generation) return@runOnUiThread
                    if(resolved==null) {
                        config.failures=(config.failures+1).coerceAtMost(30)
                        if(config.failures>=5) config.blockedUntil=System.currentTimeMillis()+ (30_000L shl (config.failures-5).coerceAtMost(5))
                        save(); toast(R.string.invalid); alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                    } else {
                        config.failures=0; config.blockedUntil=0
                        if(save()) {
                            session.unlock(resolved,config.timeout); alert.dismiss()
                            if(resolved==VeilProfile.DECOY) emergency() else showLauncher()
                        }
                    }
                }
            }
        }
    }
    private fun showWelcome() {
        val root=VeilUi.root(this)
        val body=VeilUi.scroll(this,root)
        body.addView(LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(VeilUi.icon(this@VeilLauncherActivity,R.drawable.ic_veil,40))
            addView(VeilUi.text(this@VeilLauncherActivity,getString(R.string.app_name),16f).apply { setTextColor(VeilUi.accent) },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=VeilUi.dp(this@VeilLauncherActivity,10) })
        })
        body.addView(VeilUi.text(this,getString(R.string.welcome_title),24f).apply { typeface=android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(VeilUi.text(this,getString(R.string.welcome_body),14f).apply { setTextColor(VeilUi.muted) })
        body.addView(VeilUi.info(this,R.string.private_intro,R.string.private_intro_body,R.drawable.ic_veil).apply { val p=VeilUi.dp(this@VeilLauncherActivity,12);setPadding(p,p,p,p) })
        body.addView(VeilUi.info(this,R.string.decoy_intro,R.string.decoy_intro_body,R.drawable.ic_notes).apply { val p=VeilUi.dp(this@VeilLauncherActivity,12);setPadding(p,p,p,p) })
        body.addView(VeilUi.text(this,getString(R.string.setup_note),12f).apply { setTextColor(VeilUi.muted) })
        root.addView(VeilUi.primary(this,R.string.setup) { showSetup() })
        root.addView(VeilUi.button(this,R.string.language) { chooseLanguage() })
    }
    private fun chooseLanguage() {
        secureDialog(AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.english),getString(R.string.arabic))) { _,i ->
            val locales=LocaleList.forLanguageTags(if(i==0) "en" else "ar")
            if(Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales=locales
            else { resources.updateConfiguration(android.content.res.Configuration(resources.configuration).apply { setLocales(locales) },resources.displayMetrics); recreate() }
        })
    }
    private fun showSetup() {
        if(storageFailed || (config.pins.isNotEmpty() && session.current()!=VeilProfile.PRIVACY)) return
        generation++; wizardFields.forEach { it.text.clear() }
        val ids=listOf(R.id.private_pin_input,R.id.private_pin_confirm,R.id.decoy_pin_input,R.id.decoy_pin_confirm,R.id.limited_pin_input,R.id.limited_pin_confirm)
        val fields=listOf(R.string.privacy_pin,R.string.confirm,R.string.decoy_pin,R.string.confirm,R.string.normal_pin,R.string.confirm).mapIndexed { i,hint -> pinField(hint).apply { id=ids[i] } }
        wizardFields=fields
        var step=0
        var limited=false
        fun pairValid(index: Int): Boolean {
            val a=fields[index].text.toString().toCharArray();val b=fields[index+1].text.toString().toCharArray()
            return try { a.size in 6..12 && a.all { it in '0'..'9' } && a.contentEquals(b) } finally { a.fill('\u0000');b.fill('\u0000') }
        }
        fun draw() {
            page="setup"
            fields.forEach { (it.parent as? ViewGroup)?.removeView(it) }
            val root=VeilUi.root(this)
            backAction={ if(step>0 && !authBusy) { step--;draw() } else showLauncher() }
            VeilUi.header(this,root,R.string.setup) { backAction?.invoke() }
            val content=VeilUi.scroll(this,root)
            content.addView(VeilUi.text(this,getString(R.string.step_of,step+1),13f).apply { setTextColor(VeilUi.accent) })
            val title=listOf(R.string.privacy_pin,R.string.decoy_pin,R.string.ready_title)[step]
            val help=listOf(R.string.private_step_body,R.string.decoy_step_body,R.string.ready_body)[step]
            content.addView(VeilUi.text(this,getString(title),25f).apply { typeface=android.graphics.Typeface.DEFAULT_BOLD })
            content.addView(VeilUi.text(this,getString(help),15f).apply { setTextColor(VeilUi.muted) })
            if(step<2) {
                content.addView(fields[step*2]);content.addView(fields[step*2+1])
                content.addView(VeilUi.text(this,getString(R.string.setup_note),12f).apply { setTextColor(VeilUi.muted) })
                root.addView(VeilUi.primary(this,R.string.next_step) {
                    if(!pairValid(step*2)) { toast(R.string.pin_invalid); return@primary }
                    if(step==1 && fields[0].text.toString()==fields[2].text.toString()) { toast(R.string.pin_invalid); return@primary }
                    step++;draw()
                }.apply { id=R.id.wizard_next })
            } else {
                content.addView(VeilUi.info(this,R.string.gesture_help,R.string.gesture_body,R.drawable.ic_clock))
                val optional=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=if(limited) View.VISIBLE else View.GONE;addView(fields[4]);addView(fields[5]) }
                content.addView(Switch(this).apply { setText(R.string.add_limited);isChecked=limited;minimumHeight=VeilUi.dp(this@VeilLauncherActivity,48)
                    setOnCheckedChangeListener { _,checked -> limited=checked;optional.visibility=if(checked) View.VISIBLE else View.GONE;if(!checked) { fields[4].text.clear();fields[5].text.clear() } } })
                content.addView(optional)
                val saveButton=VeilUi.primary(this,R.string.save) {}.apply { id=R.id.pin_setup_save }
                root.addView(saveButton)
                saveButton.setOnClickListener {
                    if(authBusy) return@setOnClickListener
                    val values=fields.map { it.text.toString().toCharArray() };val pairs=values.chunked(2);val active=pairs.filter { it[0].isNotEmpty() }
                    val valid=pairs.take(2).all { it[0].size in 6..12 } && active.all { it[0].size in 6..12 && it[0].contentEquals(it[1]) && it[0].all { c -> c in '0'..'9' } } &&
                        pairs.all { it[0].isNotEmpty() || it[1].isEmpty() } && active.indices.all { i -> (i+1 until active.size).all { j -> !active[i][0].contentEquals(active[j][0]) } }
                    if(!valid) { values.forEach { it.fill('\u0000') };toast(R.string.pin_invalid);return@setOnClickListener }
                    fields.forEach { it.text.clear() };authBusy=true;saveButton.isEnabled=false
                    val token=generation
                    worker.execute {
                        val bindings=try { pairs.mapIndexedNotNull { i,pair -> if(pair[0].isEmpty()) null else PinProfileResolver.bind(pair[0],listOf(VeilProfile.PRIVACY,VeilProfile.DECOY,VeilProfile.NORMAL)[i]) } } catch (_: Exception) { null } finally { values.forEach { it.fill('\u0000') } }
                        runOnUiThread {
                            authBusy=false
                            if(isDestroyed || token!=generation || page!="setup") return@runOnUiThread
                            if(config.pins.isNotEmpty() && session.current()!=VeilProfile.PRIVACY) { emergency();return@runOnUiThread }
                            if(bindings==null) { toast(R.string.invalid);step=0;draw();return@runOnUiThread }
                            config.pins=bindings
                            if(save()) { session.unlock(VeilProfile.PRIVACY,config.timeout);wizardFields=emptyList();showControl() }
                        }
                    }
                }
            }
        }
        draw()
    }
    private fun protectedPage(title: Int): LinearLayout? {
        if(session.current()!=VeilProfile.PRIVACY || storageFailed) { showLauncher(); return null }
        page="control"
        renderedProfile=session.current()
        val root=VeilUi.root(this)
        backAction={ if(title==R.string.control) showLauncher() else showControl() }
        VeilUi.header(this,root,title) { backAction?.invoke() }
        return VeilUi.scroll(this,root)
    }
    private fun showControl() {
        val content=protectedPage(R.string.control) ?: return
        content.addView(VeilUi.text(this,getString(R.string.control_intro),23f).apply { typeface=android.graphics.Typeface.DEFAULT_BOLD })
        content.addView(VeilUi.text(this,getString(R.string.control_intro_body),14f).apply { setTextColor(VeilUi.muted) })
        val actions: List<Pair<Triple<Int,Int,Int>,() -> Unit>> = listOf(
            Triple(R.string.visibility,R.string.visibility_hint,R.drawable.ic_notes) to { secureDialog(AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.privacy),getString(R.string.decoy),getString(R.string.normal))) { _,i -> showApps(listOf(VeilProfile.PRIVACY,VeilProfile.DECOY,VeilProfile.NORMAL)[i]) }) },
            Triple(R.string.profiles,R.string.profiles_hint,R.drawable.ic_veil) to { showSetup() },
            Triple(R.string.timeout,R.string.timeout_hint,R.drawable.ic_clock) to { val minutes=intArrayOf(1,5,15,30);secureDialog(AlertDialog.Builder(this).setItems(minutes.map { getString(R.string.minutes,it) }.toTypedArray()) { _,i -> if(session.current()==VeilProfile.PRIVACY) { config.timeout=minutes[i]*60_000L;if(save()) session.unlock(VeilProfile.PRIVACY,config.timeout) } }) },
            Triple(R.string.private_space,R.string.private_hint,R.drawable.ic_veil) to { showPrivate() },
            Triple(R.string.default_home,R.string.home_hint,R.drawable.ic_settings) to { requestHome() },
            Triple(R.string.about,R.string.about_hint,R.drawable.ic_notes) to { protectedPage(R.string.about)?.apply { addView(VeilUi.info(this@VeilLauncherActivity,R.string.gesture_help,R.string.gesture_body,R.drawable.ic_clock));addView(VeilUi.text(this@VeilLauncherActivity,getString(R.string.limits))) } }
        )
        actions.chunked(2).forEach { pair ->
            val row=LinearLayout(this)
            pair.forEachIndexed { index,(spec,run) -> row.addView(VeilUi.action(this,spec.first,spec.second,spec.third,run),LinearLayout.LayoutParams(0,-2,1f).apply { topMargin=VeilUi.dp(this@VeilLauncherActivity,6);bottomMargin=VeilUi.dp(this@VeilLauncherActivity,6);if(index==1) marginStart=VeilUi.dp(this@VeilLauncherActivity,10) }) }
            content.addView(row)
        }
        content.addView(VeilUi.info(this,R.string.gesture_help,R.string.gesture_body,R.drawable.ic_clock))
        content.addView(Switch(this).apply { setText(R.string.emergency);isChecked=config.emergencyEnabled;minimumHeight=VeilUi.dp(this@VeilLauncherActivity,48)
            setOnCheckedChangeListener { _,v -> if(session.current()==VeilProfile.PRIVACY) { config.emergencyEnabled=v;save() } } })
        content.addView(VeilUi.button(this,R.string.language) { chooseLanguage() })
        content.addView(VeilUi.primary(this,R.string.lock) { emergency() })
    }
    private fun showPrivate() {
        val content=protectedPage(R.string.private_space) ?: return
        content.addView(VeilUi.text(this,getString(R.string.private_unavailable)))
        if(privateLockConfirmed==false) content.addView(VeilUi.text(this,getString(R.string.private_failed)))
        content.addView(VeilUi.button(this,if(config.hidePrivate) R.string.private_show else R.string.private_hidden) { if(session.current()==VeilProfile.PRIVACY) { config.hidePrivate=!config.hidePrivate; if(save()) showPrivate() } })
        space.profiles().forEach { user ->
            content.addView(VeilUi.text(this,getString(if(space.locked(user)) R.string.locked else R.string.unlocked)))
            content.addView(VeilUi.button(this,if(space.locked(user)) R.string.private_unlock else R.string.private_lock) {
                if(session.current()==VeilProfile.PRIVACY) { if(!space.setLocked(user,!space.locked(user))) toast(R.string.private_pending); showPrivate() }
            })
        }
        if(Build.VERSION.SDK_INT>=36 && space.available()) content.addView(VeilUi.button(this,R.string.settings) {
            if(session.current()==VeilProfile.PRIVACY) runCatching { catalogSettings() }.onFailure { toast(R.string.private_unavailable) }
        })
    }
    private fun catalogSettings() {
        if(Build.VERSION.SDK_INT>=36) getSystemService(android.content.pm.LauncherApps::class.java).privateSpaceSettingsIntent?.let { startIntentSender(it,null,0,0,0) } ?: toast(R.string.private_unavailable)
    }
    private fun showApps(profile: VeilProfile) {
        val content=protectedPage(R.string.visibility) ?: return
        content.addView(VeilUi.text(this,getString(R.string.configure_profile,getString(when(profile) { VeilProfile.PRIVACY -> R.string.privacy;VeilProfile.DECOY -> R.string.decoy;else -> R.string.normal })),21f))
        content.addView(VeilUi.text(this,getString(R.string.profile_apps_help),14f).apply { setTextColor(VeilUi.muted) })
        val search=EditText(this).apply { id=R.id.management_search;setHint(R.string.manage_search);isSingleLine=true;VeilUi.input(this@VeilLauncherActivity,this) };content.addView(search)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };content.addView(list)
        val apps=catalog.load(ProfilePolicy(VeilProfile.PRIVACY),true,true)
        fun render(query: String) {
            if(session.current()!=VeilProfile.PRIVACY) { emergency();return }
            list.removeAllViews()
            apps.filter { it.label.contains(query,true) }.forEach { app ->
                val row=VeilUi.panel(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                row.addView(ImageView(this).apply { setImageDrawable(app.icon);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(VeilUi.dp(this,40),VeilUi.dp(this,40)))
                val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                labels.addView(VeilUi.text(this,app.label,16f))
                val rule=config.rules.firstOrNull { it.target.packageName==app.packageName && it.target.userSerial==app.identity.userSerial && profile in it.profiles }
                val included=profile==VeilProfile.PRIVACY || app.packageName in config.allowlists[profile].orEmpty() || "${app.packageName}|${app.identity.userSerial}" in config.allowlists[profile].orEmpty()
                labels.addView(VeilUi.text(this,getString(if(!included) R.string.excluded else when(rule?.presentation) { VeilPresentation.HIDDEN -> R.string.hidden;VeilPresentation.DISGUISED -> R.string.disguised;VeilPresentation.DECOY -> R.string.decoy;else -> R.string.real }),12f).apply { setTextColor(VeilUi.muted) })
                row.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=VeilUi.dp(this@VeilLauncherActivity,12) })
                row.isClickable=true;row.isFocusable=true;row.setOnClickListener { editApp(app,profile) };list.addView(row)
            }
        }
        render("");search.addTextChangedListener(SimpleTextWatcher { render(it) })
    }
    private fun editApp(app: LaunchableApp, profile: VeilProfile) {
        if(session.current()!=VeilProfile.PRIVACY) { emergency(); return }
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; val padding=VeilUi.dp(this@VeilLauncherActivity,16);setPadding(padding,padding,padding,padding) }
        val old=config.rules.firstOrNull { it.target.packageName==app.packageName && it.target.userSerial==app.identity.userSerial && profile in it.profiles }
        val allow=CheckBox(this).apply { setText(R.string.allow); isChecked=profile==VeilProfile.PRIVACY || ("${app.packageName}|${app.identity.userSerial}" in config.allowlists[profile].orEmpty() || app.packageName in config.allowlists[profile].orEmpty()); isEnabled=profile!=VeilProfile.PRIVACY }; form.addView(allow)
        val presentation=Spinner(this).apply { id=R.id.presentation_picker;adapter=ArrayAdapter(this@VeilLauncherActivity,android.R.layout.simple_spinner_dropdown_item,listOf(R.string.real,R.string.disguised,R.string.hidden,R.string.decoy).map { getString(it) }); setSelection(old?.presentation?.ordinal ?: 0) }; form.addView(presentation)
        val explanation=VeilUi.text(this,"",14f).apply { setTextColor(VeilUi.muted) };form.addView(explanation)
        presentation.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { explanation.setText(listOf(R.string.real_help,R.string.disguised_help,R.string.hidden_help,R.string.decoy_help)[position]) }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val label=EditText(this).apply { setHint(R.string.label); setText(old?.decoyLabel ?: app.label); filters=arrayOf(android.text.InputFilter.LengthFilter(40)) }; form.addView(label)
        form.addView(VeilUi.text(this,getString(R.string.destination)))
        val keys=listOf("calculator","notes","clock")
        val utility=Spinner(this).apply { adapter=ArrayAdapter(this@VeilLauncherActivity,android.R.layout.simple_spinner_dropdown_item,listOf(R.string.calculator,R.string.notes,R.string.clock).map { getString(it) }); setSelection(keys.indexOf(old?.decoyIconKey).coerceAtLeast(0)) }; form.addView(utility)
        val alert=AlertDialog.Builder(this).setTitle(app.label).setView(ScrollView(this).apply { addView(form) }).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save) { _,_ ->
            if(session.current()==VeilProfile.PRIVACY) {
                config.allowlists[profile]=config.allowlists[profile].orEmpty().let { if(allow.isChecked) (it-app.packageName)+"${app.packageName}|${app.identity.userSerial}" else it-app.packageName-"${app.packageName}|${app.identity.userSerial}" }
                config.rules=config.rules.filterNot { it.target.packageName==app.packageName && it.target.userSerial==app.identity.userSerial && profile in it.profiles } + DisguiseRule(app.identity.copy(className=null),VeilPresentation.entries[presentation.selectedItemPosition],label.text.toString().ifBlank { app.label },keys[utility.selectedItemPosition],setOf(profile))
                save()
            } else emergency()
        }.create()
        dialog=alert; alert.show(); alert.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun secureDialog(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog=builder.create().also { it.show(); it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    private fun requestHome() {
        if(Build.VERSION.SDK_INT>=29) {
            val rm=getSystemService(RoleManager::class.java)
            if(rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),100) else toast(R.string.default_selected)
        } else startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
    }
}
