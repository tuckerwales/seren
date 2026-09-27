package wales.tucker.seren.edit.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyKeyTest {
    @Test
    fun keysEditTheText() {
        val state = TextFieldState("if (x) {\n  y\n}", initialSelection = TextRange(12))
        applyKey(state, EditorKey.Text(";"))
        assertEquals("if (x) {\n  y;\n}", state.text.toString())
        applyKey(state, EditorKey.Home)
        assertEquals(TextRange(11), state.selection)
        applyKey(state, EditorKey.Tab)
        assertEquals("if (x) {\n    y;\n}", state.text.toString())
        applyKey(state, EditorKey.Up)
        applyKey(state, EditorKey.End)
        assertEquals(TextRange(8), state.selection)
    }
}
