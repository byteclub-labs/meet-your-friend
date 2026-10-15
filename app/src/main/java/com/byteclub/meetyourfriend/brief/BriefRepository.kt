package com.byteclub.meetyourfriend.brief

import com.byteclub.meetyourfriend.data.ConnectDao
import com.byteclub.meetyourfriend.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class BriefRepository @Inject constructor(
    private val dao: ConnectDao,
    private val settings: SettingsRepository,
    private val keys: GeminiKeyStore,
    private val gemini: GeminiClient,
    private val voiceNoteMaker: VoiceNoteMaker,
) {
    suspend fun generate(connectId: Long): Boolean {
        val key = keys.getKey() ?: return false
        val connect = dao.getById(connectId) ?: return false
        val config = settings.settings.first()
        val brief = gemini.brief(
            key = key,
            model = config.briefModel,
            name = connect.name,
            organization = connect.organization,
            role = connect.role,
            linkedinUrl = connect.linkedinUrl,
        )
        val saved = connect.copy(
            briefText = brief.text,
            briefSources = GeminiClient.sourcesJson(brief.sources),
            briefConfident = brief.confident,
            voiceNotePath = null,
        )
        dao.upsert(saved)
        val voicePath = voiceNoteMaker.synthesize(brief.text, connect.id)
        if (voicePath != null) dao.upsert(saved.copy(voiceNotePath = voicePath))
        return true
    }
}
