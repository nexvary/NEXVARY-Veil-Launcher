package com.nexvary.veil.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.nexvary.veil.auth.PinProfileBinding
import com.nexvary.veil.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VeilConfig {
    var pins = listOf<PinProfileBinding>()
    var rules = listOf<DisguiseRule>()
    var allowlists = VeilProfile.entries.associateWith { emptySet<String>() }.toMutableMap()
    var timeout = 300_000L
    var failures = 0
    var blockedUntil = 0L
    var hidePrivate = false
    var emergencyEnabled = true
    fun policy(profile: VeilProfile) = ProfilePolicy(profile, allowlists[profile].orEmpty(), rules, enforceAllowlist = profile != VeilProfile.PRIVACY)
}

class VeilStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "veil-config.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("veil.config.v1", null) as? SecretKey)?.let { return it }
        check(!file.baseFile.exists()) { "Key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("veil.config.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): VeilConfig {
        if (!file.baseFile.exists()) return VeilConfig()
        val data = file.readFully()
        require(data.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0,12)))
        val plain = cipher.doFinal(data.copyOfRange(12,data.size))
        try {
            val json = JSONObject(String(plain, Charsets.UTF_8))
            require(json.getInt("version") == 1)
            return VeilConfig().apply {
                val a = json.getJSONArray("pins")
                pins = (0 until a.length()).map { i -> a.getJSONObject(i).let {
                    PinProfileBinding(decode(it.getString("hash")), VeilProfile.valueOf(it.getString("profile")), decode(it.getString("salt")), it.getInt("iterations"))
                } }
                require(pins.isEmpty() || (pins.map { it.profile }.toSet().containsAll(setOf(VeilProfile.PRIVACY, VeilProfile.DECOY))))
                val r = json.getJSONArray("rules")
                rules = (0 until r.length()).map { i -> r.getJSONObject(i).let {
                    DisguiseRule(AppIdentity(it.getString("package"), it.getString("class"), it.getLong("user")),
                        VeilPresentation.valueOf(it.getString("presentation")), it.getString("label").ifBlank { null }, it.getString("icon").ifBlank { null },
                        setOf(VeilProfile.valueOf(it.getString("profile"))))
                } }
                VeilProfile.entries.forEach { profile ->
                    val list = json.getJSONArray(profile.name)
                    allowlists[profile] = (0 until list.length()).map { list.getString(it) }.toSet()
                }
                timeout = json.getLong("timeout").also { require(it in 30_000..3_600_000) }
                failures = json.getInt("failures").coerceAtLeast(0)
                blockedUntil = json.getLong("blockedUntil")
                hidePrivate = json.optBoolean("hidePrivate")
                emergencyEnabled = json.optBoolean("emergencyEnabled", true)
            }
        } finally { plain.fill(0) }
    }
    @Synchronized fun save(config: VeilConfig) {
        val json = JSONObject().put("version",1).put("timeout",config.timeout).put("failures",config.failures)
            .put("blockedUntil",config.blockedUntil).put("hidePrivate",config.hidePrivate).put("emergencyEnabled",config.emergencyEnabled)
        json.put("pins", JSONArray().apply { config.pins.forEach { put(JSONObject().put("profile",it.profile.name)
            .put("salt",encode(it.salt)).put("hash",encode(it.digest)).put("iterations",it.iterations)) } })
        json.put("rules", JSONArray().apply { config.rules.forEach { rule -> rule.profiles.forEach { profile ->
            put(JSONObject().put("package",rule.target.packageName).put("class",rule.target.className ?: "")
                .put("user",rule.target.userSerial).put("presentation",rule.presentation.name)
                .put("label",rule.decoyLabel ?: "").put("icon",rule.decoyIconKey ?: "").put("profile",profile.name))
        } } })
        VeilProfile.entries.forEach { json.put(it.name,JSONArray(config.allowlists[it].orEmpty().toList())) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val plain = json.toString().toByteArray()
        val encrypted = try { cipher.iv + cipher.doFinal(plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes,Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text,Base64.NO_WRAP)
}
