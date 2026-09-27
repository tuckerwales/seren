package wales.tucker.seren.files

import android.content.Context
import wales.tucker.seren.files.data.AppDatabase
import wales.tucker.seren.files.data.SettingsRepository
import wales.tucker.seren.files.data.TrashBin
import wales.tucker.seren.files.fs.AndroidStorage
import wales.tucker.seren.files.fs.Storage
import wales.tucker.seren.files.ops.Operations
import wales.tucker.seren.files.ui.common.Thumbnails

/** Manual dependency container, created once by [SerenApp]. */
class AppContainer(
    context: Context,
    val storage: Storage = AndroidStorage(context.applicationContext),
    val now: () -> Long = System::currentTimeMillis,
) {
    val context: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val bookmarks = database.bookmarks()
    val trash = TrashBin(database.trash(), storage, now)
    val operations = Operations(this.context, storage, trash, bookmarks)
    val thumbnails = Thumbnails()

    /** Whether Seren Files is on screen, so a finished job only notifies when people are elsewhere. */
    @Volatile
    var appVisible = false
}
