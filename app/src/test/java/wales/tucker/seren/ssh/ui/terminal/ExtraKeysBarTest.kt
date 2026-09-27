package wales.tucker.seren.ssh.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.ssh.TestApp
import wales.tucker.seren.ssh.emulator.TerminalKey

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class ExtraKeysBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun keysCanBeUsedThroughAccessibility() {
        val modifiers = StickyModifiers()
        val keys = mutableListOf<TerminalKey>()
        var toggles = 0
        compose.setContent {
            ExtraKeysBar(
                modifiers = modifiers,
                background = Color.Black,
                foreground = Color.White,
                accent = Color.Blue,
                onKey = { keys += it },
                onText = {},
                onToggleKeyboard = { toggles++ },
            )
        }
        compose.onNodeWithContentDescription("Escape").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(TerminalKey.ESCAPE), keys)

        compose.onNodeWithContentDescription("Toggle keyboard").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, toggles)

        val control = compose.onNodeWithContentDescription("Control")
        control.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
        control.performSemanticsAction(SemanticsActions.OnClick)
        control.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On for the next key"))
        assertEquals(ModifierState.ONCE, modifiers.ctrl)
    }

    @Test
    fun hiddenKeysAreLeftOut() {
        compose.setContent {
            ExtraKeysBar(
                modifiers = StickyModifiers(),
                background = Color.Black,
                foreground = Color.White,
                accent = Color.Blue,
                onKey = {},
                onText = {},
                onToggleKeyboard = {},
                hidden = setOf("|", "F12"),
            )
        }
        compose.onNodeWithContentDescription("Pipe").assertDoesNotExist()
        compose.onNodeWithContentDescription("F12").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tilde").assertExists()
    }
}
