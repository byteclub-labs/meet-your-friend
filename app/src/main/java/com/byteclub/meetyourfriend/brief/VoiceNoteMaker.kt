package com.byteclub.meetyourfriend.brief

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class VoiceNoteMaker @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun synthesize(text: String, connectId: Long): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val directory = File(context.filesDir, "voice").apply { mkdirs() }
                val output = File(directory, "$connectId.wav")
                lateinit var engine: TextToSpeech
                engine = TextToSpeech(context) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        engine.shutdown()
                        if (continuation.isActive) continuation.resume(null)
                        return@TextToSpeech
                    }
                    engine.language = java.util.Locale.getDefault()
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            engine.shutdown()
                            if (continuation.isActive) continuation.resume(output.absolutePath)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            engine.shutdown()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                    val result = engine.synthesizeToFile(
                        text,
                        Bundle(),
                        output,
                        "connect-$connectId",
                    )
                    if (result != TextToSpeech.SUCCESS) {
                        engine.shutdown()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation { engine.shutdown() }
            }
        }
}
