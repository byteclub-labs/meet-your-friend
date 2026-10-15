package com.byteclub.meetyourfriend.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizerTest {
    @Test
    fun preservesInternationalNumberAcrossRegions() {
        assertEquals("+971501234567", PhoneNormalizer("US").normalize("+971501234567"))
    }

    @Test
    fun normalizesRegionalNumber() {
        assertEquals("+971501234567", PhoneNormalizer("AE").normalize("050 123 4567"))
    }

    @Test
    fun rejectsShortNumber() {
        assertEquals(null, PhoneNormalizer("US").normalize("12"))
    }
}
