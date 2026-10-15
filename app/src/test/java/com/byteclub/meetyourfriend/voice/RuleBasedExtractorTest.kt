package com.byteclub.meetyourfriend.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedExtractorTest {
    private val extractor = RuleBasedExtractor()

    @Test
    fun extractsPriyaFromSpokenIntroduction() {
        assertEquals(
            ExtractedFields("Priya Sharma", "Acme Corp", "Product Lead", "+971501234567"),
            extractor.extractRules(
                "This is Priya Sharma, she's a Product Lead at Acme Corp, number plus nine seven one five zero one two three four five six seven",
            ),
        )
    }

    @Test
    fun extractsJohnFromSpokenIntroduction() {
        assertEquals(
            ExtractedFields("John Lee", "Globex", "CTO", "5551234567"),
            extractor.extractRules(
                "Meet John Lee from Globex, he works as CTO, his number is 555 123 4567",
            ),
        )
    }

    @Test
    fun extractsAnnaFromSpokenIntroduction() {
        assertEquals(
            ExtractedFields("Anna", "Initech", "designer", "99876543210"),
            extractor.extractRules(
                "Anna works for Initech as a designer mobile double nine eight seven six five four three two one zero",
            ),
        )
    }

    @Test
    fun leavesUnrecognizedSpeechEmpty() {
        assertEquals(ExtractedFields(), extractor.extractRules("hello there"))
    }

    @Test
    fun handlesDigitWordsAndTriple() {
        assertEquals("7772024", extractor.extractRules("mobile triple seven two zero two four").mobile)
    }
}
