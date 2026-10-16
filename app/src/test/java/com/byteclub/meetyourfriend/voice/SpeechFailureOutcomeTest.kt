package com.byteclub.meetyourfriend.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechFailureOutcomeTest {
    @Test
    fun serverErrorsCountTowardFallbackThreshold() {
        val first = failureOutcome(error = 4, failuresSoFar = 0)
        assertEquals(true, first.countsAsFailure)
        assertEquals(false, first.showFallback)

        val second = failureOutcome(error = 4, failuresSoFar = 1)
        assertEquals(true, second.countsAsFailure)
        assertEquals(true, second.showFallback)
    }

    @Test
    fun recognizerBusyDoesNotCount() {
        val outcome = failureOutcome(error = 8, failuresSoFar = 1)
        assertEquals("Recognizer busy — tap to retry", outcome.message)
        assertEquals(false, outcome.countsAsFailure)
        assertEquals(false, outcome.showFallback)
    }

    @Test
    fun insufficientPermissionsShowsFallbackImmediately() {
        val outcome = failureOutcome(error = 9, failuresSoFar = 0)
        assertEquals(true, outcome.countsAsFailure)
        assertEquals(true, outcome.showFallback)
    }

    @Test
    fun watchdogTimeoutCountsAsFailure() {
        val outcome = failureOutcome(error = null, failuresSoFar = 0)
        assertEquals("Didn't catch that — tap to retry", outcome.message)
        assertEquals(true, outcome.countsAsFailure)
        assertEquals(false, outcome.showFallback)
    }

    @Test
    fun networkErrorsExplainTheConnectionRequirement() {
        assertEquals("Speech needs a network connection", failureOutcome(1, 0).message)
        assertEquals("Speech needs a network connection", failureOutcome(2, 0).message)
    }

    @Test
    fun noMatchAndSpeechTimeoutAskForAnotherAttempt() {
        assertEquals("Didn't catch that — tap to retry", failureOutcome(6, 0).message)
        assertEquals("Didn't catch that — tap to retry", failureOutcome(7, 0).message)
    }
}
