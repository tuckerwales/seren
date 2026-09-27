package wales.tucker.terminal.ui.terminal

import android.app.Activity
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl
import wales.tucker.terminal.TestApp

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class TerminalViewBlinkTest {
    @Test
    fun blinkTimerOnlyRunsWhileFocused() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = TerminalView(activity)
        activity.setContentView(view)
        Shadow.extract<ShadowViewRootImpl>(view.rootView.parent).callWindowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        view.clearFocus()
        assertTrue(view.hasWindowFocus())
        assertFalse("unfocused", view.blinkScheduled)

        view.requestFocus()
        assertTrue("focused", view.blinkScheduled)

        Shadow.extract<ShadowViewRootImpl>(view.rootView.parent).callWindowFocusChanged(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("window unfocused", view.blinkScheduled)
        Shadow.extract<ShadowViewRootImpl>(view.rootView.parent).callWindowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("window focused again", view.blinkScheduled)

        view.cursorBlinkEnabled = false
        assertFalse("blink setting off", view.blinkScheduled)
    }
}
