package com.byteclub.meetyourfriend.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onFailure("Speech recognition is unavailable", true)
            return
        }
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listener.onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listener.onListeningChanged(false)
            }

            override fun onError(error: Int) {
                listener.onListeningChanged(false)
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_CLIENT) {
                    failures++
                }
                val fallback = failures >= 2
                listener.onFailure(
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) "Didn't catch that — tap to retry"
                    else "Speech recognition failed",
                    fallback,
                )
                if (fallback) stop()
            }

            override fun onResults(results: Bundle?) {
                listener.onListeningChanged(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    failures = 0
                    listener.onTranscript(text)
                }
                stop()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(listener::onPartial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer.startListening(intent)
        listener.onListeningChanged(true)
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.stopListening() }
            it.destroy()
        }
        recognizer = null
        listener.onListeningChanged(false)
    }

    fun destroy() = stop()
}
