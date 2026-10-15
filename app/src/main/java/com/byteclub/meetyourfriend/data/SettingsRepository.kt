package com.byteclub.meetyourfriend.data

import android.content.Context
import android.telephony.TelephonyManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val myName: String = "",
    val whatsappTemplate: String =
        "Hi {name}, it was great meeting you today! Looking forward to staying in touch. – {myName}",
    val defaultCountryCode: String = "",
    val extractionModel: String = "gemini-2.5-flash",
    val briefModel: String = "gemini-2.5-pro",
    val autoBrief: Boolean = true,
    val locationTagging: Boolean = false,
    val privacyNoticeAccepted: Boolean = false,
)

class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val myName = stringPreferencesKey("my_name")
        val whatsappTemplate = stringPreferencesKey("whatsapp_template")
        val defaultCountryCode = stringPreferencesKey("default_country_code")
        val extractionModel = stringPreferencesKey("extraction_model")
        val briefModel = stringPreferencesKey("brief_model")
        val autoBrief = booleanPreferencesKey("auto_brief")
        val locationTagging = booleanPreferencesKey("location_tagging")
        val privacyNoticeAccepted = booleanPreferencesKey("privacy_notice_accepted")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { preferences ->
        AppSettings(
            myName = preferences[Keys.myName].orEmpty(),
            whatsappTemplate = preferences[Keys.whatsappTemplate]
                ?: AppSettings().whatsappTemplate,
            defaultCountryCode = preferences[Keys.defaultCountryCode].orEmpty(),
            extractionModel = preferences[Keys.extractionModel] ?: "gemini-2.5-flash",
            briefModel = preferences[Keys.briefModel] ?: "gemini-2.5-pro",
            autoBrief = preferences[Keys.autoBrief] ?: true,
            locationTagging = preferences[Keys.locationTagging] ?: false,
            privacyNoticeAccepted = preferences[Keys.privacyNoticeAccepted] ?: false,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings.first())
        context.settingsStore.edit { preferences ->
            preferences[Keys.myName] = updated.myName
            preferences[Keys.whatsappTemplate] = updated.whatsappTemplate
            preferences[Keys.defaultCountryCode] = updated.defaultCountryCode
            preferences[Keys.extractionModel] = updated.extractionModel
            preferences[Keys.briefModel] = updated.briefModel
            preferences[Keys.autoBrief] = updated.autoBrief
            preferences[Keys.locationTagging] = updated.locationTagging
            preferences[Keys.privacyNoticeAccepted] = updated.privacyNoticeAccepted
        }
    }

    suspend fun resolvedRegion(): String {
        val configured = settings.first().defaultCountryCode.trim()
        if (configured.isNotEmpty()) return configured.uppercase(Locale.ROOT)
        val networkCountry = runCatching {
            context.getSystemService(TelephonyManager::class.java)
                ?.networkCountryIso
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        return networkCountry?.uppercase(Locale.ROOT)
            ?: Locale.getDefault().country.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)
            ?: "US"
    }
}
