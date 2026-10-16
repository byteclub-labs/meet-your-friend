package com.byteclub.meetyourfriend.voice

import javax.inject.Inject

data class ExtractedFields(
    val name: String? = null,
    val organization: String? = null,
    val role: String? = null,
    val mobile: String? = null,
)

interface FieldExtractor {
    suspend fun extract(transcript: String): ExtractedFields
}

class RuleBasedExtractor @Inject constructor() : FieldExtractor {
    override suspend fun extract(transcript: String): ExtractedFields = extractRules(transcript)

    fun extractRules(transcript: String): ExtractedFields {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return ExtractedFields()
        return ExtractedFields(
            name = extractName(cleaned),
            organization = extractOrganization(cleaned),
            role = extractRole(cleaned),
            mobile = extractMobile(cleaned),
        )
    }

    private fun extractName(text: String): String? {
        val cue = Regex("(?i)\\b(?:this is|meet|my friend)\\s+")
        val match = cue.find(text)
        val candidate = if (match != null) {
            captureUntilStop(text.substring(match.range.last + 1))
        } else {
            val leading = Regex("^\\s*([A-Z][\\p{L}'’-]*(?:\\s+[A-Z][\\p{L}'’-]*)*)")
                .find(text)?.groupValues?.get(1)
            leading?.takeIf {
                Regex("(?i)\\b(?:works|working|is|from)\\b").containsMatchIn(text)
            }
        }
        return candidate?.trim()?.takeIf(String::isNotBlank)
    }

    private fun extractOrganization(text: String): String? {
        val cue = Regex("(?i)\\b(?:works\\s+for|working\\s+at|works\\s+at|at|from)\\s+")
        val match = cue.find(text) ?: return null
        return captureUntilStop(
            text.substring(match.range.last + 1),
            extraStops = listOf(" as "),
        )?.trim()?.takeIf(String::isNotBlank)
    }

    private fun extractRole(text: String): String? {
        val cue = Regex("(?i)\\b(?:works\\s+as|she's\\s+(?:a|an)|he's\\s+(?:a|an)|is\\s+(?:a|an)|as\\s+(?:a|an)|as)\\s+")
        val match = cue.find(text) ?: return null
        return captureUntilStop(
            text.substring(match.range.last + 1),
            extraStops = listOf(" at ", " from "),
        )?.trim()?.takeIf(String::isNotBlank)
    }

    private fun extractMobile(text: String): String? {
        val match = Regex("(?i)\\b(?:number|mobile|phone)(?:\\s+is)?\\s+(.+)$")
            .find(text) ?: return null
        val tokens = Regex("[+]?\\d+|[a-z]+").findAll(match.groupValues[1].lowercase())
            .map { it.value }
            .toList()
        val result = StringBuilder()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token == "plus" && result.isEmpty() -> result.append('+')
                token == "double" || token == "triple" -> {
                    val count = if (token == "double") 2 else 3
                    val next = tokens.getOrNull(index + 1)
                    val digit = digitFor(next)
                    if (digit != null) repeat(count) { result.append(digit) }
                    index++
                }
                token == "oh" || token == "zero" -> result.append('0')
                digitFor(token) != null -> result.append(digitFor(token))
                token.matches(Regex("\\+?\\d+")) -> result.append(token)
            }
            index++
        }
        val value = result.toString()
        return value.takeIf { it.count(Char::isDigit) >= 7 }
    }

    private fun digitFor(word: String?): Char? = when (word) {
        "one" -> '1'
        "two" -> '2'
        "three" -> '3'
        "four" -> '4'
        "five" -> '5'
        "six" -> '6'
        "seven" -> '7'
        "eight" -> '8'
        "nine" -> '9'
        "zero", "oh" -> '0'
        else -> null
    }

    private fun captureUntilStop(value: String, extraStops: List<String> = emptyList()): String? {
        val standardStops = listOf(
            ",",
            " at ",
            " from ",
            " as ",
            " he ",
            " she ",
            "his number",
            "her number",
            " number",
            " mobile",
            " phone",
        )
        val lowered = value.lowercase()
        val stop = (standardStops + extraStops).mapNotNull { phrase ->
            val index = lowered.indexOf(phrase)
            index.takeIf { it >= 0 }
        }.minOrNull() ?: value.length
        return value.substring(0, stop).trim().trimEnd('.', '!', '?', ';')
    }
}

class PhoneNormalizer(private val defaultRegion: String) {
    fun normalize(raw: String): String? = runCatching {
        val util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance()
        val parsed = util.parse(raw, defaultRegion)
        if (util.isValidNumber(parsed)) util.format(parsed, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164) else null
    }.getOrNull()
}
