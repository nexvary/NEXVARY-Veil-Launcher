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
    private var generation=0
    private val worker=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private var dialog: AlertDialog?=null
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
        onBackPressedDispatcher.addCallback(this, object: androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { showLauncher() } })
        try { config=store.load() } catch (_: Exception) { storageFailed=true }
        val filter=IntentFilter().apply {
            if(Build.VERSION.SDK_INT>=35) { addAction(Intent.ACTION_PROFILE_AVAILABLE); addAction(Intent.ACTION_PROFILE_UNAVAILABLE) }
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(profileReceiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(profileReceiver,filter)
        privateLockConfirmed=space.lockAll()
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
        generation++; dialog?.dismiss(); handler.removeCallbacksAndMessages(null); worker.shutdownNow()
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
        page="home"
        val profile=session.current()
        renderedProfile=profile
        val root=VeilUi.root(this,profile!=VeilProfile.DECOY)
        val clock=TextClock(this).apply {
            format24Hour="HH:mm"; format12Hour="hh:mm a"; textSize=38f; gravity=Gravity.CENTER; id=R.id.home_clock
            setOnLongClickListener { if(!storageFailed) { if(config.pins.isEmpty()) showSetup() else showPin() }; true }
        }
        root.addView(clock)
        val search=EditText(this).apply { id=R.id.app_search; isSaveEnabled=false; setHint(R.string.search); isSingleLine=true
            setOnLongClickListener { if(config.emergencyEnabled) emergency(); true } }
        root.addView(search)
        if(storageFailed) root.addView(VeilUi.text(this,getString(R.string.storage_error)))
        if(config.pins.isEmpty() && !storageFailed) root.addView(VeilUi.button(this,R.string.setup) { showSetup() })
        val grid=GridView(this).apply {
            id=R.id.app_grid; columnWidth=VeilUi.dp(this@VeilLauncherActivity,88); numColumns=GridView.AUTO_FIT
            stretchMode=GridView.STRETCH_COLUMN_WIDTH; verticalSpacing=VeilUi.dp(this@VeilLauncherActivity,8)
        }
        root.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        fun render(query: String) {
            val current=session.current()
            val apps=if(storageFailed) emptyList() else catalog.load(config.policy(current))
            val entries=apps.filter { it.label.contains(query,true) }.map { Tile(it.label,it) }.toMutableList()
            listOf(R.string.calculator to "calculator",R.string.notes to "notes",R.string.clock to "clock",R.string.settings to "settings").forEach { (id,key) ->
                val label=getString(id)
                // A configured app/decoy with this label already provides the tile.
                // Avoid duplicate Settings/Calculator/Clock entries in a convincing profile.
                if(label.contains(query,true) && entries.none { it.label.equals(label,true) }) entries+=Tile(label,utility=key)
            }
            grid.adapter=TileAdapter(entries)
            grid.setOnItemClickListener { _,_,position,_ ->
                val tile=entries[position]
                if(tile.app!=null) {
                    // Re-resolve against the current policy at click time to close stale-view races.
                    val fresh=catalog.load(config.policy(session.current())).firstOrNull { it.identity==tile.app.identity }
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
                val privateGrid=GridView(this).apply { columnWidth=VeilUi.dp(this@VeilLauncherActivity,88); numColumns=GridView.AUTO_FIT; stretchMode=GridView.STRETCH_COLUMN_WIDTH }
                val items=catalog.load(config.policy(profile),includePrivate=true).filter { it.privateSpace }.map { Tile(it.label,it) }
                privateGrid.adapter=TileAdapter(items)
                privateGrid.setOnItemClickListener { _,_,position,_ ->
                    if(session.current()!=VeilProfile.PRIVACY) { emergency(); return@setOnItemClickListener }
                    val app=catalog.load(config.policy(VeilProfile.PRIVACY),true).firstOrNull { it.identity==items[position].app?.identity }
                    if(app!=null && app.decision.launchRealTarget) catalog.launch(app)
                    else if(app!=null) utility(app.decision.iconOverrideKey ?: "calculator")
                }
                root.addView(privateGrid,LinearLayout.LayoutParams(-1,VeilUi.dp(this,144)))
            }
        }
        if(profile==VeilProfile.PRIVACY) {
            val row=LinearLayout(this)
            row.addView(VeilUi.button(this,R.string.control) { showControl() },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(VeilUi.button(this,R.string.lock) { emergency() },LinearLayout.LayoutParams(0,-2,1f)); root.addView(row)
        }
    }
    private data class Tile(val label: String,val app: LaunchableApp?=null,val utility: String?=null)
    private inner class TileAdapter(private val items: List<Tile>): BaseAdapter() {
        override fun getCount()=items.size
        override fun getItem(position: Int)=items[position]
        override fun getItemId(position: Int)=position.toLong()
        override fun getView(position: Int, convert: View?, parent: ViewGroup?): View {
            val tile=items[position]
            return LinearLayout(this@VeilLauncherActivity).apply {
                orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; background=VeilUi.card()
                setPadding(6,14,6,10); minimumHeight=VeilUi.dp(this@VeilLauncherActivity,100)
                addView(ImageView(this@VeilLauncherActivity).apply {
                    if(tile.app?.icon!=null) setImageDrawable(tile.app.icon)
                    else setImageResource(when(tile.utility ?: tile.app?.decision?.iconOverrideKey) {
                        "notes" -> R.drawable.ic_notes; "clock" -> R.drawable.ic_clock; "settings" -> R.drawable.ic_settings; else -> R.drawable.ic_calculator
                    })
                },LinearLayout.LayoutParams(VeilUi.dp(this@VeilLauncherActivity,38),VeilUi.dp(this@VeilLauncherActivity,38)))
                addView(VeilUi.text(this@VeilLauncherActivity,tile.label,13f).apply { gravity=Gravity.CENTER; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
            }
        }
    }
    private fun pinField(hint: Int) = EditText(this).apply {
        setHint(hint); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
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
    private fun showSetup() {
        if(storageFailed || (config.pins.isNotEmpty() && session.current()!=VeilProfile.PRIVACY)) return
        page="setup"
        val root=VeilUi.root(this)
        root.addView(VeilUi.button(this,R.string.back) { showLauncher() })
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(form) },LinearLayout.LayoutParams(-1,0,1f))
        form.addView(VeilUi.text(this,getString(R.string.setup_help)))
        val fieldIds=listOf(R.id.private_pin_input,R.id.private_pin_confirm,R.id.decoy_pin_input,R.id.decoy_pin_confirm,R.id.limited_pin_input,R.id.limited_pin_confirm)
        val fields=listOf(R.string.privacy_pin,R.string.confirm,R.string.decoy_pin,R.string.confirm,R.string.normal_pin,R.string.confirm).mapIndexed { i,hint -> pinField(hint).apply { id=fieldIds[i] }.also(form::addView) }
        val saveButton=VeilUi.button(this,R.string.save) {}.apply { id=R.id.pin_setup_save }
        form.addView(saveButton)
        saveButton.setOnClickListener {
            if(authBusy) return@setOnClickListener
            val values=fields.map { it.text.toString().toCharArray() }
            val pairs=values.chunked(2)
            val active=pairs.filter { it[0].isNotEmpty() }
            val valid= pairs.take(2).all { it[0].size in 6..12 } && active.all { it[0].size in 6..12 && it[0].contentEquals(it[1]) && it[0].all { c -> c in '0'..'9' } } &&
                pairs.all { it[0].isNotEmpty() || it[1].isEmpty() } && active.indices.all { i -> (i+1 until active.size).all { j -> !active[i][0].contentEquals(active[j][0]) } }
            fields.forEach { it.text.clear() }
            if(!valid) { values.forEach { it.fill('\u0000') }; toast(R.string.pin_invalid); return@setOnClickListener }
            authBusy=true; saveButton.isEnabled=false
            val token=generation
            worker.execute {
                val bindings=try { pairs.mapIndexedNotNull { i,pair -> if(pair[0].isEmpty()) null else PinProfileResolver.bind(pair[0],listOf(VeilProfile.PRIVACY,VeilProfile.DECOY,VeilProfile.NORMAL)[i]) } } catch (_: Exception) { null } finally { values.forEach { it.fill('\u0000') } }
                runOnUiThread {
                    authBusy=false
                    if(isDestroyed || token!=generation || page!="setup") return@runOnUiThread
                    if(config.pins.isNotEmpty() && session.current()!=VeilProfile.PRIVACY) { emergency(); return@runOnUiThread }
                    if(bindings==null) { toast(R.string.invalid); saveButton.isEnabled=true; return@runOnUiThread }
                    config.pins=bindings
                    if(save()) { session.unlock(VeilProfile.PRIVACY,config.timeout); showControl() }
                }
            }
        }
    }
    private fun protectedPage(title: Int): LinearLayout? {
        if(session.current()!=VeilProfile.PRIVACY || storageFailed) { showLauncher(); return null }
        page="control"
        renderedProfile=session.current()
        val root=VeilUi.root(this)
        root.addView(VeilUi.button(this,R.string.back) { showLauncher() })
        root.addView(VeilUi.text(this,getString(title),24f))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        return content
    }
    private fun showControl() {
        val content=protectedPage(R.string.control) ?: return
        content.addView(VeilUi.button(this,R.string.profiles) { showSetup() })
        content.addView(VeilUi.button(this,R.string.visibility) {
            secureDialog(AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.privacy),getString(R.string.decoy),getString(R.string.normal))) { _,i -> showApps(listOf(VeilProfile.PRIVACY,VeilProfile.DECOY,VeilProfile.NORMAL)[i]) })
        })
        content.addView(VeilUi.button(this,R.string.timeout) {
            val minutes=intArrayOf(1,5,15,30)
            secureDialog(AlertDialog.Builder(this).setItems(minutes.map { getString(R.string.minutes,it) }.toTypedArray()) { _,i -> if(session.current()==VeilProfile.PRIVACY) { config.timeout=minutes[i]*60_000L; if(save()) session.unlock(VeilProfile.PRIVACY,config.timeout) } })
        })
        content.addView(Switch(this).apply { setText(R.string.emergency); isChecked=config.emergencyEnabled
            setOnCheckedChangeListener { _,v -> if(session.current()==VeilProfile.PRIVACY) { config.emergencyEnabled=v; save() } } })
        content.addView(VeilUi.button(this,R.string.private_space) { showPrivate() })
        content.addView(VeilUi.button(this,R.string.default_home) { requestHome() })
        content.addView(VeilUi.button(this,R.string.about) { protectedPage(R.string.about)?.addView(VeilUi.text(this,getString(R.string.limits))) })
        content.addView(VeilUi.button(this,R.string.lock) { emergency() })
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
        catalog.load(ProfilePolicy(VeilProfile.PRIVACY),true,true).forEach { app ->
            content.addView(Button(this).apply { text=app.label; isAllCaps=false; setOnClickListener { editApp(app,profile) } })
        }
    }
    private fun editApp(app: LaunchableApp, profile: VeilProfile) {
        if(session.current()!=VeilProfile.PRIVACY) { emergency(); return }
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,12) }
        val old=config.rules.firstOrNull { it.target.packageName==app.packageName && it.target.userSerial==app.identity.userSerial && profile in it.profiles }
        val allow=CheckBox(this).apply { setText(R.string.allow); isChecked=profile==VeilProfile.PRIVACY || ("${app.packageName}|${app.identity.userSerial}" in config.allowlists[profile].orEmpty() || app.packageName in config.allowlists[profile].orEmpty()); isEnabled=profile!=VeilProfile.PRIVACY }; form.addView(allow)
        val presentation=Spinner(this).apply { adapter=ArrayAdapter(this@VeilLauncherActivity,android.R.layout.simple_spinner_dropdown_item,listOf(R.string.real,R.string.disguised,R.string.hidden,R.string.decoy).map { getString(it) }); setSelection(old?.presentation?.ordinal ?: 0) }; form.addView(presentation)
        val label=EditText(this).apply { setHint(R.string.label); setText(old?.decoyLabel ?: app.label); filters=arrayOf(android.text.InputFilter.LengthFilter(40)) }; form.addView(label)
        form.addView(VeilUi.text(this,getString(R.string.destination)))
        val keys=listOf("calculator","notes","clock")
        val utility=Spinner(this).apply { adapter=ArrayAdapter(this@VeilLauncherActivity,android.R.layout.simple_spinner_dropdown_item,listOf(R.string.calculator,R.string.notes,R.string.clock).map { getString(it) }); setSelection(keys.indexOf(old?.decoyIconKey).coerceAtLeast(0)) }; form.addView(utility)
        val alert=AlertDialog.Builder(this).setTitle(app.label).setView(form).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save) { _,_ ->
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
