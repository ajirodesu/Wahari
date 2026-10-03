package dev.citali.needle.pilot.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the AI provider API key encrypted at rest.
 *
 * The key itself lives in the Android Keystore (hardware-backed where available)
 * and never leaves it. Only the AES/GCM ciphertext and IV are written to
 * preferences, so the plaintext key is never persisted unencrypted.
 */
object SecureStore {
    private const val PREFS = "needle_secure"
    private const val ALIAS = "needle_api_key"
    private const val KEY_CIPHER = "api_key_cipher"
    private const val KEY_IV = "api_key_iv"
    private const val T_ALIAS = "needle_telegram_token"
    private const val T_CIPHER = "telegram_token_cipher"
    private const val T_IV = "telegram_token_iv"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_SIZE_BITS = 128

    /**
     * True only when a key is stored *and* still decryptable.
     *
     * Checking for the ciphertext alone was misleading: if the Keystore entry is
     * lost the ciphertext survives, so the UI reported "key stored" while every
     * read returned null and the agent silently ran offline.
     */
    fun hasApiKey(context: Context): Boolean = !getApiKey(context).isNullOrBlank()

    /**
     * Encrypts and stores the key. Returns true on success.
     *
     * Uses commit() rather than apply(): the caller shows a confirmation based on
     * the result, so the write must have actually happened before we report it.
     */
    fun saveApiKey(context: Context, value: String): Boolean {
        val prefs = prefs(context)
        if (value.isBlank()) {
            return prefs.edit().remove(KEY_CIPHER).remove(KEY_IV).commit()
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val stored = prefs.edit()
                .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
            // Only report success if it can be read back.
            stored && getApiKey(context) == value
        }.getOrDefault(false)
    }

    fun getApiKey(context: Context): String? {
        val prefs = prefs(context)
        val cipherB64 = prefs.getString(KEY_CIPHER, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        val decrypted = runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadExistingKey() ?: return@runCatching null,
                GCMParameterSpec(TAG_SIZE_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()

        if (decrypted.isNullOrBlank()) {
            // The Keystore entry is gone or no longer matches this ciphertext
            // (app data restored to a new device, Keystore reset, key rotated).
            // Drop the unusable blob so the UI can honestly prompt for a new key
            // instead of insisting one is saved.
            prefs.edit().remove(KEY_CIPHER).remove(KEY_IV).apply()
            return null
        }
        return decrypted
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_CIPHER).remove(KEY_IV).commit()
    }

    /**
     * Telegram bot token, encrypted with its own Keystore entry so a key
     * rotation or loss for one secret never affects the other. Blank clears.
     * Returns the stored token, or null when none is saved (or it became
     * undecryptable, in which case the dead blob is dropped).
     */
    fun getTelegramToken(context: Context): String? =
        decryptSecret(context, T_ALIAS, T_CIPHER, T_IV)

    fun saveTelegramToken(context: Context, value: String): Boolean =
        encryptSecret(context, T_ALIAS, T_CIPHER, T_IV, value)

    private fun encryptSecret(
        context: Context,
        alias: String,
        cipherKey: String,
        ivKey: String,
        value: String,
    ): Boolean {
        val prefs = prefs(context)
        if (value.isBlank()) {
            return prefs.edit().remove(cipherKey).remove(ivKey).commit()
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(alias))
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val stored = prefs.edit()
                .putString(cipherKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
            stored && decryptSecret(context, alias, cipherKey, ivKey) == value
        }.getOrDefault(false)
    }

    private fun decryptSecret(
        context: Context,
        alias: String,
        cipherKey: String,
        ivKey: String,
    ): String? {
        val prefs = prefs(context)
        val cipherB64 = prefs.getString(cipherKey, null) ?: return null
        val ivB64 = prefs.getString(ivKey, null) ?: return null
        val decrypted = runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadExistingKey(alias) ?: return@runCatching null,
                GCMParameterSpec(TAG_SIZE_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()

        if (decrypted.isNullOrBlank()) {
            prefs.edit().remove(cipherKey).remove(ivKey).apply()
            return null
        }
        return decrypted
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The existing Keystore entry, or null if it is missing. Never creates one. */
    private fun loadExistingKey(alias: String = ALIAS): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(alias, null) as? SecretKey
    }.getOrNull()

    private fun key(alias: String = ALIAS): SecretKey {
        loadExistingKey(alias)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }
}
