package wales.tucker.seren.core.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock with biometrics or the device screen lock, the same in every Seren app. The activity
 * keeps the locked state; this does the platform work.
 */
object AppLock {
    const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** How long the app may be in the background before it locks again. */
    const val TIMEOUT_MS = 30_000L

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt with [title] (the app name) and [subtitle]. [onResult] gets true on
     * success. When the device has nothing to authenticate with, [onResult] gets false so a locked
     * app stays locked (fail-closed) rather than unlocking silently.
     */
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        if (!canAuthenticate(activity)) {
            onResult(false)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    /** Hides [activity]'s content in recent apps while app lock is on. */
    fun hideFromRecents(activity: Activity, hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            // No way to blank just the thumbnail before Android 13; this also blocks screenshots.
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
