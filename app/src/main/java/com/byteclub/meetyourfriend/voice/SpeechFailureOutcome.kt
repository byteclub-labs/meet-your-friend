package com.byteclub.meetyourfriend.voice

private const val ERROR_NETWORK_TIMEOUT = 1
private const val ERROR_NETWORK = 2
private const val ERROR_SPEECH_TIMEOUT = 6
private const val ERROR_NO_MATCH = 7
private const val ERROR_RECOGNIZER_BUSY = 8
private const val ERROR_INSUFFICIENT_PERMISSIONS = 9

internal data class FailureOutcome(
    val message: String,
    val countsAsFailure: Boolean,
    val showFallback: Boolean,
)

internal fun failureOutcome(error: Int?, failuresSoFar: Int): FailureOutcome {
    val busy = error == ERROR_RECOGNIZER_BUSY
    val countsAsFailure = !busy
    val failuresAfter = failuresSoFar + if (countsAsFailure) 1 else 0
    val message = when (error) {
        ERROR_NETWORK_TIMEOUT, ERROR_NETWORK -> "Speech needs a network connection"
        ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH, null -> "Didn't catch that — tap to retry"
        ERROR_RECOGNIZER_BUSY -> "Recognizer busy — tap to retry"
        else -> "Speech recognition failed"
    }
    return FailureOutcome(
        message = message,
        countsAsFailure = countsAsFailure,
        showFallback = error == ERROR_INSUFFICIENT_PERMISSIONS || (!busy && failuresAfter >= 2),
    )
}
