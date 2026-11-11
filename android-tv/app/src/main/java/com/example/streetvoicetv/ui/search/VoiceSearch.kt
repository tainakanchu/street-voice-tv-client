package com.example.streetvoicetv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Language asked of the speech recognizer first (falls back to the device default). */
internal const val VOICE_SEARCH_LANGUAGE = "zh-TW"

/**
 * The speech recognition intent. With [language] the recognizer is asked for that language;
 * without, it uses the device default.
 */
internal fun voiceSearchIntent(language: String?): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "說出歌曲、音樂人或歌單名稱")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (language != null) {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        }
    }

/**
 * Whether a speech recognizer activity exists. On Android 11+ this needs the
 * `RECOGNIZE_SPEECH` intent declared under `<queries>` in the manifest (package visibility).
 */
internal fun isVoiceSearchAvailable(context: Context): Boolean =
    voiceSearchIntent(null).resolveActivity(context.packageManager) != null

/**
 * Remembers a voice search launcher, or null when the device has no speech recognizer (the
 * mic button is then hidden). The recognised text goes to [onResult]. The first attempt asks
 * for [VOICE_SEARCH_LANGUAGE]; if the recognizer fails with an error (e.g. it does not support
 * that language) it is asked once more in the device default language.
 */
@Composable
internal fun rememberVoiceSearch(onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    var available by remember { mutableStateOf(isVoiceSearchAvailable(context)) }
    val currentOnResult by rememberUpdatedState(onResult)
    // Whether the running attempt already is the device-default fallback.
    val fallback = remember { BooleanArray(1) }
    lateinit var launch: (Intent) -> Unit
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull { it.isNotBlank() }
                if (text != null) currentOnResult(text)
            }
            Activity.RESULT_CANCELED -> Unit
            // RecognizerIntent.RESULT_*_ERROR: retry once without the language.
            else -> if (!fallback[0]) {
                fallback[0] = true
                launch(voiceSearchIntent(null))
            }
        }
    }
    launch = { intent ->
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            available = false
        }
    }
    if (!available) return null
    return {
        fallback[0] = false
        launch(voiceSearchIntent(VOICE_SEARCH_LANGUAGE))
    }
}
