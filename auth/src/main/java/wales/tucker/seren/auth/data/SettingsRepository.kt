package wales.tucker.seren.auth.data

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

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Show "••• •••" until a code is tapped. */
    val hideCodes: Boolean = false,
    /** Show the next code under the current one when it is about to change. */
    val showNextCode: Boolean = true,
    val appLock: Boolean = false,
    /** Block screenshots and screen recording, which also hides the app in recent apps. */
    val blockScreenshots: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIDE_CODES = booleanPreferencesKey("hide_codes")
        val SHOW_NEXT_CODE = booleanPreferencesKey("show_next_code")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: d.dynamicColor,
            hideCodes = p[Keys.HIDE_CODES] ?: d.hideCodes,
            showNextCode = p[Keys.SHOW_NEXT_CODE] ?: d.showNextCode,
            appLock = p[Keys.APP_LOCK] ?: d.appLock,
            blockScreenshots = p[Keys.BLOCK_SCREENSHOTS] ?: d.blockScreenshots,
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setHideCodes(v: Boolean) = context.dataStore.edit { it[Keys.HIDE_CODES] = v }
    suspend fun setShowNextCode(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_NEXT_CODE] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = v }
    suspend fun setBlockScreenshots(v: Boolean) = context.dataStore.edit { it[Keys.BLOCK_SCREENSHOTS] = v }
}
