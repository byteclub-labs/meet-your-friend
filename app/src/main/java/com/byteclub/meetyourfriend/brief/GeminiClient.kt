package com.byteclub.meetyourfriend.brief

import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class BriefSource(val title: String, val uri: String)
data class BriefResult(val text: String, val confident: Boolean, val sources: List<BriefSource>)

data class ParsedBrief(val briefText: String, val confident: Boolean)

object BriefParser {
    private val confidenceLine = Regex("^CONFIDENCE:\\s*HIGH\\s*$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedBrief {
        val lines = text.lines()
        val lastNonBlank = lines.indexOfLast(String::isNotBlank)
        val confident = lastNonBlank >= 0 && confidenceLine.matches(lines[lastNonBlank].trim())
        val brief = if (lastNonBlank >= 0 &&
            Regex("^CONFIDENCE:\\s*(?:HIGH|LOW)\\s*$", RegexOption.IGNORE_CASE)
                .matches(lines[lastNonBlank].trim())
        ) {
            lines.take(lastNonBlank).joinToString("\n").trim()
        } else {
            text.trim()
        }
        return ParsedBrief(brief, confident)
    }
}

class GeminiClient @Inject constructor() {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun extract(
        key: String,
        model: String,
        transcript: String,
    ): com.byteclub.meetyourfriend.voice.ExtractedFields {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", EXTRACT_SYSTEM) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "Transcript: \"$transcript\"") })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0)
                put("responseMimeType", "application/json")
                put("responseSchema", extractionSchema())
            })
        }
        val responseText = post(key, model, body.toString())
        return parseExtractionText(candidateText(responseText))
    }

    suspend fun brief(
        key: String,
        model: String,
        name: String,
        organization: String,
        role: String,
        linkedinUrl: String?,
    ): BriefResult {
        val prompt = BRIEF_PROMPT
            .replace("{name}", name.ifBlank { "unknown" })
            .replace("{organization}", organization.ifBlank { "unknown" })
            .replace("{role}", role.ifBlank { "unknown" })
            .replace("{linkedinUrl}", linkedinUrl?.takeIf(String::isNotBlank) ?: "unknown")
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            put("tools", buildJsonArray {
                add(buildJsonObject { put("google_search", buildJsonObject {}) })
            })
            put("generationConfig", buildJsonObject { put("temperature", 0.3) })
        }
        return parseBriefPayload(post(key, model, body.toString()))
    }

    suspend fun testKey(key: String, model: String) {
        extract(key, model, "This is Test User from Example Inc")
    }

    private suspend fun post(key: String, model: String, body: String): String {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody(mediaType))
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                check(response.isSuccessful) { "Gemini request failed (${response.code})" }
                responseBody
            }
        }
    }

    private fun extractionSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put("properties", buildJsonObject {
            listOf("name", "organization", "role", "mobile").forEach { field ->
                put(field, buildJsonObject {
                    put("type", "STRING")
                    put("nullable", true)
                })
            }
        })
        put("required", buildJsonArray {
            listOf("name", "organization", "role", "mobile").forEach {
                add(JsonPrimitive(it))
            }
        })
    }

    companion object {
        const val EXTRACT_SYSTEM =
            "You extract contact details from a short spoken introduction that was transcribed by speech-to-text. Return JSON with keys name, organization, role, mobile. Use null for anything that is not stated. Do not guess or invent values. Convert spoken numbers to digits (for example 'double five' becomes '55' and 'plus nine seven one' becomes '+971'). mobile must contain only an optional leading + followed by digits. Write name as the person's full name in title case."

        const val BRIEF_PROMPT =
            "Write a short brief (about 150 words) about the person below, for someone who has just met them and wants to follow up. Use Google Search to find public information, including their public LinkedIn profile if one exists.\n" +
                "Name: {name}\n" +
                "Organization: {organization}\n" +
                "Role: {role}\n" +
                "LinkedIn profile: {linkedinUrl}\n" +
                "\n" +
                "Cover: 1) who they are and what they do, 2) what their organization does, 3) two or three specific conversation follow-ups. Write plain text with no markdown. Only state facts about this specific person that you found in the search results. If you cannot confidently identify this exact person, say so in the first sentence and describe only the organization and the role.\n" +
                "On the very last line write exactly CONFIDENCE: HIGH if the search results clearly match this person, otherwise CONFIDENCE: LOW."

        fun candidateText(payload: String): String {
            val root = Json.parseToJsonElement(payload).jsonObject
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("Gemini response did not contain a candidate")
            return candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                .joinToString("") { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
        }

        fun parseExtractionText(text: String): com.byteclub.meetyourfriend.voice.ExtractedFields {
            val item = Json.parseToJsonElement(text).jsonObject
            fun nullable(name: String) = item[name]?.jsonPrimitive?.contentOrNull
            return com.byteclub.meetyourfriend.voice.ExtractedFields(
                name = nullable("name"),
                organization = nullable("organization"),
                role = nullable("role"),
                mobile = nullable("mobile"),
            )
        }

        fun parseBriefPayload(payload: String): BriefResult {
            val root = Json.parseToJsonElement(payload).jsonObject
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("Gemini response did not contain a candidate")
            val content = candidate["content"]?.jsonObject
            val parts = content?.get("parts")?.jsonArray ?: JsonArray(emptyList())
            val text = parts.joinToString("") { part ->
                part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val parsed = BriefParser.parse(text)
            val chunks = candidate["groundingMetadata"]?.jsonObject
                ?.get("groundingChunks")?.jsonArray ?: JsonArray(emptyList())
            val sources = chunks.mapNotNull { chunk ->
                val web = chunk.jsonObject["web"]?.jsonObject ?: return@mapNotNull null
                val uri = web["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                BriefSource(
                    title = web["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    uri = uri,
                )
            }
            return BriefResult(parsed.briefText, parsed.confident, sources)
        }

        fun sourcesJson(sources: List<BriefSource>): String = buildJsonArray {
            sources.forEach { source ->
                add(buildJsonObject {
                    put("title", source.title)
                    put("uri", source.uri)
                })
            }
        }.toString()
    }
}
