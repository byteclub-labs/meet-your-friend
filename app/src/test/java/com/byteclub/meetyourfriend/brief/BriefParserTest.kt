package com.byteclub.meetyourfriend.brief

import org.junit.Assert.assertEquals
import org.junit.Test

class BriefParserTest {
    @Test
    fun parsesHighConfidence() {
        assertEquals(ParsedBrief("A short brief.", true), BriefParser.parse("A short brief.\nCONFIDENCE: HIGH\n"))
    }

    @Test
    fun parsesLowConfidence() {
        assertEquals(ParsedBrief("A short brief.", false), BriefParser.parse("A short brief.\nCONFIDENCE: LOW"))
    }

    @Test
    fun missingConfidenceIsUnverified() {
        assertEquals(ParsedBrief("A short brief.", false), BriefParser.parse("A short brief."))
    }

    @Test
    fun parsesCannedExtractionAndGroundedBriefResponses() {
        val extraction = checkNotNull(javaClass.getResource("/extraction-response.json")).readText()
        assertEquals(
            com.byteclub.meetyourfriend.voice.ExtractedFields("Test User", "Example Inc", null, "+12025550123"),
            GeminiClient.parseExtractionText(GeminiClient.candidateText(extraction)),
        )

        val brief = checkNotNull(javaClass.getResource("/grounded-brief-response.json")).readText()
        val parsed = GeminiClient.parseBriefPayload(brief)
        assertEquals("A useful follow-up brief.", parsed.text)
        assertEquals(true, parsed.confident)
        assertEquals(
            listOf(BriefSource("Example", "https://example.com/person")),
            parsed.sources,
        )
    }
}
