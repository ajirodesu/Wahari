package dev.citali.needle.engine

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice-mode helpers that do not touch the microphone or speaker:
 * reply shortening and recognizer-error text.
 */
class VoiceModeTest {

    @Test
    fun shortRepliesPassThrough() {
        assertEquals("Battery is at 87 percent.", VoiceMode.sanitizeForSpeech("Battery is at 87 percent."))
        assertEquals("", VoiceMode.sanitizeForSpeech("   "))
    }

    @Test
    fun codeFencesAndUrlsAreNotReadAloud() {
        val raw = "Done. ```adb shell foo``` See https://example.com/x *bold* #tag"
        val clean = VoiceMode.sanitizeForSpeech(raw)
        assertTrue("```" !in clean && "https://" !in clean && "*" !in clean && "#" !in clean)
        assertTrue(clean.contains("Done."))
    }

    @Test
    fun longRepliesCutAtASentence() {
        val raw = ("First sentence here. Second sentence here! " +
            "Third sentence here? ").repeat(20)
        val clean = VoiceMode.sanitizeForSpeech(raw)
        assertTrue(clean.length <= 620)
        assertTrue(clean.endsWith('.') || clean.endsWith('!') || clean.endsWith('?') || clean.endsWith('…'))
    }

    @Test
    fun errorCodesHaveText() {
        assertEquals("nothing heard", VoiceMode.describeError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals("silence", VoiceMode.describeError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals("microphone blocked", VoiceMode.describeError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals("network error", VoiceMode.describeError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals("code 4242", VoiceMode.describeError(4242))
    }
}
