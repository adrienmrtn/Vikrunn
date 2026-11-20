@file:UseSerializers(InstantSerializer::class)

package com.atala.app.core.account

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.atala.app.core.model.Account
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The session tokens of the signed-in account, as they are kept in the vault. */
@Serializable
data class AuthKeychainSession(
    @SerialName("userID") val userId: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Instant? = null,
)

/**
 * The session tokens, out of the app's JSON file (AuthServiceKeychain.swift): encrypted with an
 * AES-GCM key that never leaves the Android Keystore, stored in private preferences excluded from
 * backups (the key would not exist on another phone anyway). The rest of the account (id, e-mail)
 * stays in the app's file.
 */
object AuthKeychain {
    private const val KEY_ALIAS = "atala.session"
    private const val PREFS = "atala.session"
    private const val ITEM = "supabase-session"

    private var prefs: SharedPreferences? = null
    private val lock = Any()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun load(): AuthKeychainSession? = synchronized(lock) {
        val stored = prefs?.getString(ITEM, null) ?: return null
        runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
            AtalaJson.decodeFromString(AuthKeychainSession.serializer(), String(plain, Charsets.UTF_8))
        }.getOrNull()
    }

    fun save(session: AuthKeychainSession): Boolean = synchronized(lock) {
        val prefs = prefs ?: return false
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val plain = AtalaJson.encodeToString(AuthKeychainSession.serializer(), session).toByteArray(Charsets.UTF_8)
            val sealed = cipher.iv + cipher.doFinal(plain)
            prefs.edit().putString(ITEM, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
        }.getOrDefault(false)
    }

    fun clear() = synchronized(lock) {
        prefs?.edit()?.remove(ITEM)?.commit()
    }

    /** Keeps the vault in step with the account written to the file. */
    fun sync(account: Account?) {
        val session = account?.keychainSession
        if (session == null) {
            if (account == null || account.isGuest) clear()
            return
        }
        if (load() != session) save(session)
    }

    /**
     * The account as read from the file, with its tokens back from the vault. A file without an
     * account (first launch after a reinstall, guest) drops any leftover session.
     */
    fun restore(account: Account?): Account? {
        if (account == null || account.isGuest) {
            clear()
            return account
        }
        // Files written before the vault still hold their tokens: they move at the next save.
        if (account.keychainSession != null) return account
        val stored = load()?.takeIf { it.userId == account.userId } ?: return account
        return account.copy(accessToken = stored.accessToken, refreshToken = stored.refreshToken, expiresAt = stored.expiresAt)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

/** The tokens of a real account, ready for the vault. Null for a guest or without tokens. */
val Account.keychainSession: AuthKeychainSession?
    get() = if (isGuest || (accessToken == null && refreshToken == null)) null
    else AuthKeychainSession(userId, accessToken, refreshToken, expiresAt)

/** The same account without its tokens: what is written to the JSON file. */
val Account.withoutTokens: Account get() = copy(accessToken = null, refreshToken = null, expiresAt = null)
