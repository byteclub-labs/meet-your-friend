package com.byteclub.meetyourfriend.voice

import com.byteclub.meetyourfriend.brief.GeminiClient
import com.byteclub.meetyourfriend.brief.GeminiKeyStore
import com.byteclub.meetyourfriend.data.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SmartFieldExtractor @Inject constructor(
    private val keys: GeminiKeyStore,
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val rules: RuleBasedExtractor,
) : FieldExtractor {
    override suspend fun extract(transcript: String): ExtractedFields {
        val key = keys.getKey() ?: return rules.extractRules(transcript)
        return runCatching {
            gemini.extract(key, settings.settings.first().extractionModel, transcript)
        }.getOrElse { rules.extractRules(transcript) }
    }
}
