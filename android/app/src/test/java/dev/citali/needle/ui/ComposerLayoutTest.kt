package dev.citali.needle.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composer mode/line-count decisions and draft truncation. The measuring
 * itself lives in the composable; the rules are pure and pinned here.
 */
class ComposerLayoutTest {

    @Test
    fun emptyStaysSingleLine() {
        val layout = composerLayout(
            hasText = false, hasNewline = false, singleWidthDp = 0f,
            inlineBudgetDp = 200f, wrappedLines = 1, maxLines = 6,
        )
        assertFalse(layout.stacked)
        assertEquals(1, layout.visibleLines)
    }

    @Test
    fun longSingleLineStacks() {
        val layout = composerLayout(
            hasText = true, hasNewline = false, singleWidthDp = 260f,
            inlineBudgetDp = 200f, wrappedLines = 2, maxLines = 6,
        )
        assertTrue(layout.stacked)
        assertEquals(2, layout.visibleLines)
    }

    @Test
    fun newlineAlwaysStacks() {
        val layout = composerLayout(
            hasText = true, hasNewline = true, singleWidthDp = 10f,
            inlineBudgetDp = 200f, wrappedLines = 3, maxLines = 6,
        )
        assertTrue(layout.stacked)
        assertEquals(3, layout.visibleLines)
    }

    @Test
    fun lineCountCappedByMaxLines() {
        val layout = composerLayout(
            hasText = true, hasNewline = true, singleWidthDp = 10f,
            inlineBudgetDp = 200f, wrappedLines = 12, maxLines = 6,
        )
        assertEquals(6, layout.visibleLines)
        val short = composerLayout(
            hasText = true, hasNewline = true, singleWidthDp = 10f,
            inlineBudgetDp = 200f, wrappedLines = 12, maxLines = 2,
        )
        assertEquals(2, short.visibleLines)
    }

    @Test
    fun shortTextInWideBudgetStaysSingle() {
        val layout = composerLayout(
            hasText = true, hasNewline = false, singleWidthDp = 199f,
            inlineBudgetDp = 200f, wrappedLines = 1, maxLines = 6,
        )
        assertFalse(layout.stacked)
    }

    @Test
    fun truncationKeepsCaretInside() {
        val long = "x".repeat(COMPOSER_MAX_LENGTH + 500)
        val coerced = coerceComposerLength(
            TextFieldValue(long, TextRange(COMPOSER_MAX_LENGTH + 500)),
        )
        assertEquals(COMPOSER_MAX_LENGTH, coerced.text.length)
        assertEquals(TextRange(COMPOSER_MAX_LENGTH), coerced.selection)
    }

    @Test
    fun shortTextPassesThroughUntouched() {
        val value = TextFieldValue("hello", TextRange(5))
        assertEquals(value, coerceComposerLength(value))
    }
}
