package wales.tucker.seren.ssh

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import wales.tucker.seren.core.security.SecretBox
import wales.tucker.seren.ssh.data.AppDatabase
import wales.tucker.seren.ssh.data.SettingsRepository
import wales.tucker.seren.ssh.session.SessionManager
import wales.tucker.seren.ssh.session.SftpTransferNotifier

/** Manual dependency container, created once by [SerenApp]. */
class AppContainer(context: Context, val secretBox: SecretBox = SecretBox("terminal_master_key")) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val sessionManager = SessionManager(context, database, secretBox, settings, appScope)
    val transferNotifier = SftpTransferNotifier(context)
}
