package com.byteclub.meetyourfriend.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechCapture(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onListeningChanged(listening: Boolean)
        fun onPartial(text: String)
        fun onTranscript(text: String)
        fun onFailure(message: String, showFallback: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    private var failures = 0
    private var sessionActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable { onWatchdogTimeout() }

    fun start() {
        if (sessionActive) stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onListeningChanged(false)
            listener.onFailure("Speech recognition is unavailable", true)
            return
        }
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer
        sessionActive = true
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (sessionActive) listener.onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (sessionActive) listener.onListeningChanged(false)
            }

            override fun onError(error: Int) {
                if (!sessionActive) return
                cancelWatchdog()
                val outcome = failureOutcome(error, failures)
                if (outcome.countsAsFailure) failures++
                stop()
                listener.onFailure(outcome.message, outcome.showFallback)
            }

            override fun onResults(results: Bundle?) {
                if (!sessionActive) return
                cancelWatchdog()
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                stop()
                if (text.isNotBlank()) {
                    failures = 0
                    listener.onTranscript(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (sessionActive) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let(listener::onPartial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        mainHandler.postDelayed(watchdog, WATCHDOG_TIMEOUT_MS)
        listener.onListeningChanged(true)
        speechRecognizer.startListening(intent)
    }

    fun stop() {
        cancelWatchdog()
        sessionActive = false
        val activeRecognizer = recognizer
        recognizer = null
        try {
            activeRecognizer?.let {
                runCatching { it.stopListening() }
                runCatching { it.destroy() }
            }
        } finally {
            listener.onListeningChanged(false)
        }
    }

    fun destroy() = stop()

    private fun onWatchdogTimeout() {
        if (!sessionActive) return
        val outcome = failureOutcome(null, failures)
        if (outcome.countsAsFailure) failures++
        stop()
        listener.onFailure(outcome.message, outcome.showFallback)
    }

    private fun cancelWatchdog() {
        mainHandler.removeCallbacks(watchdog)
    }

    private companion object {
        const val WATCHDOG_TIMEOUT_MS = 12_000L
    }
}
