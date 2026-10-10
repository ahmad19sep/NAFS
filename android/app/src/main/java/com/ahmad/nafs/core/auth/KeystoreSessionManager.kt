package com.ahmad.nafs.core.auth

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
// Supabase must not publish an authenticated state before the encrypted session is durable.
@SuppressLint("ApplySharedPref", "UseKtx")
class KeystoreSessionManager @Inject constructor(
    @ApplicationContext context: Context,
) : SessionManager {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(json.encodeToString(session).encodeToByteArray())
        val saved = preferences.edit()
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(SESSION_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(saved) { "Unable to persist the authentication session." }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        val encodedIv = preferences.getString(IV_KEY, null) ?: throw NoSessionFoundException()
        val encodedSession = preferences.getString(SESSION_KEY, null) ?: throw NoSessionFoundException()

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
                )
            }
            val plaintext = cipher.doFinal(Base64.decode(encodedSession, Base64.NO_WRAP))
            json.decodeFromString<UserSession>(plaintext.decodeToString())
        } catch (_: Exception) {
            preferences.edit().remove(IV_KEY).remove(SESSION_KEY).commit()
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        preferences.edit().remove(IV_KEY).remove(SESSION_KEY).commit()
        Unit
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "nafs_secure_auth"
        const val IV_KEY = "session_iv"
        const val SESSION_KEY = "session_ciphertext"
        const val KEY_ALIAS = "nafs_auth_session_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
