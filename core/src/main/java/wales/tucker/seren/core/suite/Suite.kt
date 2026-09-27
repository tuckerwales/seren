package wales.tucker.seren.core.suite

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/** The apps in the suite, so each one can offer the others by name when they are installed. */
enum class SuiteApp(val packageName: String, val appName: String) {
    SSH("wales.tucker.seren.ssh", "Seren SSH"),
    EDIT("wales.tucker.seren.edit", "Seren Edit"),
    AUTH("wales.tucker.seren.auth", "Seren Auth"),
    FILES("wales.tucker.seren.files", "Seren Files"),
    ;

    companion object {
        fun of(packageName: String?): SuiteApp? = entries.firstOrNull { it.packageName == packageName }
    }
}

/**
 * How the Seren apps hand things to each other. Everything goes through ordinary Android intents
 * and content links, so each app still works alone and with apps outside the suite. Only the
 * actions below are Seren's own; the activities that handle them require [PERMISSION], which only
 * apps signed with the same key as the suite can hold.
 *
 * Nothing secret ever travels between apps: files and folders only, each through a content link
 * with a one-off grant, never a raw path or a stored password, key or setup key.
 */
object Suite {
    /** Declared by every Seren app (in Seren Core's manifest) with signature protection. */
    const val PERMISSION = "wales.tucker.seren.permission.SUITE"

    /** Seren SSH: open Import key with the private key file in the intent's data. */
    const val ACTION_IMPORT_KEY = "wales.tucker.seren.action.IMPORT_KEY"

    /** Seren Files: open the folder holding the file in the intent's data, with it picked out. */
    const val ACTION_REVEAL = "wales.tucker.seren.action.REVEAL"

    /** The file's name, for messages, when the content link doesn't say. */
    const val EXTRA_DISPLAY_NAME = "wales.tucker.seren.extra.DISPLAY_NAME"

    fun isInstalled(context: Context, app: SuiteApp): Boolean = try {
        context.packageManager.getPackageInfo(app.packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens [uri] in [app], which may save back to it when [writable]. */
    fun viewIntent(app: SuiteApp, uri: Uri, mimeType: String, writable: Boolean = false): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))

    /** Shares [uris] with [app] as the share sheet would, for example to upload them. */
    fun sendIntent(app: SuiteApp, uris: List<Uri>, mimeType: String): Intent {
        require(uris.isNotEmpty()) { "Nothing to send" }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        // The grant travels with the clip, which is what lets the receiver read every link.
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        return intent
            .setType(mimeType)
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Asks Seren SSH to import the private key in [uri]; people still confirm it there. */
    fun importKeyIntent(uri: Uri, displayName: String? = null): Intent =
        Intent(ACTION_IMPORT_KEY)
            .setData(uri)
            .setPackage(SuiteApp.SSH.packageName)
            .putExtra(EXTRA_DISPLAY_NAME, displayName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Asks Seren Files to show where [uri] is. It only reads the link, never the file. */
    fun revealIntent(uri: Uri, displayName: String? = null): Intent =
        Intent(ACTION_REVEAL)
            .setData(uri)
            .setPackage(SuiteApp.FILES.packageName)
            .putExtra(EXTRA_DISPLAY_NAME, displayName)

    /** Starts [intent] in a new task, returning false when the app is missing or refuses it. */
    fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Whether [app] is installed, checked again each time the screen resumes, so an action for it
 * appears as soon as someone installs it and disappears when they remove it.
 */
@Composable
fun rememberInstalled(app: SuiteApp): Boolean {
    val context = LocalContext.current
    var installed by remember(app) { mutableStateOf(Suite.isInstalled(context, app)) }
    LifecycleResumeEffect(app) {
        installed = Suite.isInstalled(context, app)
        onPauseOrDispose {}
    }
    return installed
}
