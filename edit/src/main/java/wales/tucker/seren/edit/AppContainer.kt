package wales.tucker.seren.edit

import android.content.Context
import wales.tucker.seren.edit.data.AppDatabase
import wales.tucker.seren.edit.data.SettingsRepository
import wales.tucker.seren.edit.document.DocumentStore

/** Manual dependency container, created once by [SerenApp]. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val documents = DocumentStore(context)
}
