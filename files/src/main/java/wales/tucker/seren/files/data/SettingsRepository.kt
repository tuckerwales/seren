package wales.tucker.seren.files.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import wales.tucker.seren.core.ui.theme.ThemeMode
import wales.tucker.seren.files.fs.SortBy
import wales.tucker.seren.files.fs.SortOrder

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val sortOrder: SortOrder = SortOrder(),
    val showHidden: Boolean = false,
    val showThumbnails: Boolean = true,
    /** Folders show a grid of large thumbnails and icons instead of a list. */
    val gridView: Boolean = false,
    /** Deleting moves things to the trash for 30 days rather than deleting them straight away. */
    val useTrash: Boolean = true,
    val appLock: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_DESCENDING = booleanPreferencesKey("sort_descending")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
        val GRID_VIEW = booleanPreferencesKey("grid_view")
        val USE_TRASH = booleanPreferencesKey("use_trash")
        val APP_LOCK = booleanPreferencesKey("app_lock")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: d.dynamicColor,
            sortOrder = SortOrder(
                by = p[Keys.SORT_BY]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() } ?: d.sortOrder.by,
                descending = p[Keys.SORT_DESCENDING] ?: d.sortOrder.descending,
            ),
            showHidden = p[Keys.SHOW_HIDDEN] ?: d.showHidden,
            showThumbnails = p[Keys.SHOW_THUMBNAILS] ?: d.showThumbnails,
            gridView = p[Keys.GRID_VIEW] ?: d.gridView,
            useTrash = p[Keys.USE_TRASH] ?: d.useTrash,
            appLock = p[Keys.APP_LOCK] ?: d.appLock,
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setSortOrder(v: SortOrder) = context.dataStore.edit {
        it[Keys.SORT_BY] = v.by.name
        it[Keys.SORT_DESCENDING] = v.descending
    }
    suspend fun setShowHidden(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_HIDDEN] = v }
    suspend fun setShowThumbnails(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_THUMBNAILS] = v }
    suspend fun setGridView(v: Boolean) = context.dataStore.edit { it[Keys.GRID_VIEW] = v }
    suspend fun setUseTrash(v: Boolean) = context.dataStore.edit { it[Keys.USE_TRASH] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = v }
}
