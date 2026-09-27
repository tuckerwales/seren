package wales.tucker.seren.edit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import wales.tucker.seren.core.content.ContentColorSchemes
import wales.tucker.seren.core.ui.theme.ThemeMode

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val colorSchemeId: String = ContentColorSchemes.DEFAULT.id,
    val fontSize: Float = 14f,
    val wordWrap: Boolean = true,
    val lineNumbers: Boolean = true,
    val showExtraKeys: Boolean = true,
    val appLock: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val LINE_NUMBERS = booleanPreferencesKey("line_numbers")
        val SHOW_EXTRA_KEYS = booleanPreferencesKey("show_extra_keys")
        val APP_LOCK = booleanPreferencesKey("app_lock")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: d.dynamicColor,
            colorSchemeId = p[Keys.COLOR_SCHEME] ?: d.colorSchemeId,
            fontSize = p[Keys.FONT_SIZE] ?: d.fontSize,
            wordWrap = p[Keys.WORD_WRAP] ?: d.wordWrap,
            lineNumbers = p[Keys.LINE_NUMBERS] ?: d.lineNumbers,
            showExtraKeys = p[Keys.SHOW_EXTRA_KEYS] ?: d.showExtraKeys,
            appLock = p[Keys.APP_LOCK] ?: d.appLock,
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setColorScheme(id: String) = context.dataStore.edit { it[Keys.COLOR_SCHEME] = id }
    suspend fun setFontSize(v: Float) = context.dataStore.edit { it[Keys.FONT_SIZE] = v.coerceIn(MIN_FONT, MAX_FONT) }
    suspend fun setWordWrap(v: Boolean) = context.dataStore.edit { it[Keys.WORD_WRAP] = v }
    suspend fun setLineNumbers(v: Boolean) = context.dataStore.edit { it[Keys.LINE_NUMBERS] = v }
    suspend fun setShowExtraKeys(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_EXTRA_KEYS] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = v }

    companion object {
        const val MIN_FONT = 8f
        const val MAX_FONT = 32f
    }
}
