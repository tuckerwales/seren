package wales.tucker.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import wales.tucker.terminal.data.AppDatabase
import wales.tucker.terminal.data.SettingsRepository
import wales.tucker.terminal.security.SecretBox
import wales.tucker.terminal.session.SessionManager

/** Manual dependency container, created once by [TerminalApp]. */
class AppContainer(context: Context, val secretBox: SecretBox = SecretBox()) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val sessionManager = SessionManager(context, database, secretBox, settings, appScope)
}
