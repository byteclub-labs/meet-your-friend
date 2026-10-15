package com.byteclub.meetyourfriend.brief

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "gemini_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getKey(): String? = preferences.getString(API_KEY, null)?.takeIf(String::isNotBlank)

    fun setKey(value: String) {
        preferences.edit().putString(API_KEY, value.trim()).apply()
    }

    fun hasKey(): Boolean = getKey() != null

    companion object {
        private const val API_KEY = "api_key"
    }
}
