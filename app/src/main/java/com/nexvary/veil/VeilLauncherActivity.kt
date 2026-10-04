package com.nexvary.veil

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.nexvary.veil.core.VeilProfile
import com.nexvary.veil.launcher.AppCatalog

class VeilLauncherActivity : Activity() {
    private val catalog by lazy { AppCatalog(this) }
    private var profile = VeilProfile.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLauncher()
        requestHomeRoleIfNeeded()
    }

    private fun showLauncher() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
        }
        val title = TextView(this).apply {
            text = "NEXVARY Veil"
            textSize = 26f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val search = EditText(this).apply {
            hint = "Search apps"
            isSingleLine = true
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(title)
        root.addView(search)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val apps = catalog.load(profile, emptyList())
        fun render(query: String) {
            list.removeAllViews()
            apps.filter { it.label.contains(query, ignoreCase = true) }.forEach { app ->
                list.addView(Button(this).apply {
                    text = app.label
                    isAllCaps = false
                    setOnClickListener { catalog.launch(app) }
                })
            }
        }
        render("")
        search.setOnEditorActionListener { _, _, _ -> false }
        search.addTextChangedListener(SimpleTextWatcher { render(it) })
    }

    private fun requestHomeRoleIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 100)
            }
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
