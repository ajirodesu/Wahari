package dev.citali.needle.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Hands-free voice mode: listen → command → spoken reply → listen again,
 * until the user stops it. Dictation (one-shot text into the field) is NOT
 * handled here; that stays a system-recognizer intent in ChatScreen.
 *
 * All entry points hop to the main thread: SpeechRecognizer and TextToSpeech
 * both require it, while tool coroutines do not have it.
 */
object VoiceMode {

    enum class StartResult { STARTED, NEED_PERMISSION, NO_RECOGNIZER, ALREADY_ON }

    data class State(
        val active: Boolean = false,
        val listening: Boolean = false,
        val speaking: Boolean = false,
    )

    private const val UTTERANCE_ID = "wahari-voice-reply"
    private const val MAX_SPEAK_CHARS = 600

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var onCommand: ((String) -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isRecognizerAvailable(context: Context): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun start(
        context: Context,
        onCommand: (String) -> Unit,
        onStatus: (String) -> Unit,
    ): StartResult {
        if (_state.value.active) return StartResult.ALREADY_ON
        val app = context.applicationContext
        if (!hasPermission(app)) return StartResult.NEED_PERMISSION
        if (!isRecognizerAvailable(app)) return StartResult.NO_RECOGNIZER
        this.onCommand = onCommand
        this.onStatus = onStatus
        _state.value = State(active = true)
        ensureTts(app)
        main.post { beginListening(app) }
        return StartResult.STARTED
    }

    fun stop() {
        main.post {
            _state.value = State(active = false)
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            runCatching { tts?.stop() }
            onCommand = null
            onStatus = null
        }
    }

    /** Speak a reply, then go back to listening when finished. */
    fun speak(context: Context, text: String) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) {
            resumeListening(context)
            return
        }
        main.post {
            if (!_state.value.active) return@post
            ensureTts(context.applicationContext)
            _state.value = _state.value.copy(listening = false, speaking = true)
            runCatching {
                tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            }.onFailure {
                _state.value = _state.value.copy(speaking = false)
                resumeListening(context.applicationContext)
            }
        }
    }

    private fun ensureTts(app: Context) {
        if (tts != null) return
        ttsReady = false
        tts = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false
                return@TextToSpeech
            }
            val tts = tts ?: return@TextToSpeech
            val applied = runCatching { tts.setLanguage(Locale.getDefault()) }
                .getOrDefault(TextToSpeech.LANG_MISSING_DATA)
            if (applied == TextToSpeech.LANG_MISSING_DATA || applied == TextToSpeech.LANG_NOT_SUPPORTED) {
                runCatching { tts.setLanguage(Locale.ENGLISH) }
            }
            ttsReady = true
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) {
                    main.post {
                        _state.value = _state.value.copy(speaking = false)
                        resumeListening(app)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    main.post {
                        _state.value = _state.value.copy(speaking = false)
                        resumeListening(app)
                    }
                }
            })
        }
    }

    private fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun beginListening(app: Context) {
        if (!_state.value.active || _state.value.speaking) return
        if (recognizer == null) {
            recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(app) }.getOrNull()
                ?: run {
                    halt("No speech recogniser on this device.")
                    return
                }
            recognizer?.setRecognitionListener(VoiceListener(app))
        }
        _state.value = _state.value.copy(listening = true)
        runCatching { recognizer?.startListening(recognizerIntent()) }
            .onFailure { halt("Could not start listening.") }
    }

    private fun resumeListening(app: Context) {
        if (!_state.value.active || _state.value.speaking) return
        beginListening(app)
    }

    private fun halt(message: String) {
        val status = onStatus
        _state.value = State(active = false)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.stop() }
        onCommand = null
        onStatus = null
        status?.invoke(message)
    }

    private class VoiceListener(private val app: Context) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(listening = false)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            _state.value = _state.value.copy(listening = false)
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
            if (!_state.value.active) return
            if (heard.isNullOrBlank()) {
                // Nothing usable: keep the loop alive rather than dropping out.
                resumeListening(app)
                return
            }
            // Recognizer goes idle after results; the reply's onDone restarts it.
            onCommand?.invoke(heard.trim())
        }

        override fun onError(error: Int) {
            _state.value = _state.value.copy(listening = false)
            if (!_state.value.active) return
            when (error) {
                // Transient: just listen again.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> resumeListening(app)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    halt("Microphone permission was revoked. Voice mode off.")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Our own recognizer is wedged; rebuild it once.
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    resumeListening(app)
                }
                else -> halt("Voice error (${describeError(error)}). Voice mode off.")
            }
        }
    }

    /**
     * Shorten replies for speech: first sentences up to the cap, no URLs or
     * code fences read aloud. Pure, covered by unit tests.
     */
    fun sanitizeForSpeech(text: String): String {
        var clean = text.replace(Regex("```[\\s\\S]*?```"), " ")
        clean = clean.replace(Regex("https?://\\S+"), " ")
        clean = clean.replace(Regex("[*_`#>\\-|]"), "")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= MAX_SPEAK_CHARS) return clean
        val cut = clean.take(MAX_SPEAK_CHARS)
        val sentenceEnd = listOf(". ", "! ", "? ").map { cut.lastIndexOf(it) }.maxOrNull() ?: -1
        return if (sentenceEnd > MAX_SPEAK_CHARS / 2) cut.substring(0, sentenceEnd + 1) else "$cut…"
    }

    /** Human text for recognizer error codes. Pure, covered by unit tests. */
    fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "microphone busy"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone blocked"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "nothing heard"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "silence"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language unsupported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language unavailable"
        else -> "code $code"
    }
}
