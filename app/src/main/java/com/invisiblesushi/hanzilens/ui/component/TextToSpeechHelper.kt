package com.invisiblesushi.hanzilens.ui.component

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import java.util.UUID

private const val TAG = "HanziLensTTS"

/**
 * Small helper that wraps Android TTS in a Compose-friendly lifecycle.
 *
 * Returns a `speak(text)` lambda. TTS is shut down automatically when the
 * composable leaves composition.
 */
@Composable
fun rememberTextToSpeech(): (text: String, locale: Locale?) -> Unit {
    val context: Context = LocalContext.current
    var ready by remember { mutableStateOf(false) }

    // TextToSpeech must be created on the main thread (Compose does that).
    val tts = remember {
        TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
                // Ignore shutdown issues.
            }
        }
    }

    val speak: (String, Locale?) -> Unit = { text: String, locale: Locale? ->
        if (ready) {

            val lang = locale ?: Locale.US
            try {
                val result = tts.setLanguage(lang)
                // Some devices return missing data / unsupported variants.
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
            } catch (_: Exception) {
                // Keep default language if setting fails.
            }

            try {
                tts.stop()
                tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    UUID.randomUUID().toString()
                )
            } catch (e: Exception) {
                Log.w(TAG, "TTS speak failed: ${e.message}")
            }
        }
    }

    return speak
}

