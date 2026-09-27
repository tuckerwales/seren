package wales.tucker.seren.auth

import android.content.Context
import wales.tucker.seren.auth.data.AccountRepository
import wales.tucker.seren.auth.data.AppDatabase
import wales.tucker.seren.auth.data.SettingsRepository
import wales.tucker.seren.core.security.SecretBox

/** Manual dependency container, created once by [SerenApp]. */
class AppContainer(
    context: Context,
    secretBox: SecretBox = SecretBox("seren_auth_master_key"),
    val clock: Clock = SystemClock,
) {
    val context: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val accounts = AccountRepository(database, secretBox, clock::now)
}
