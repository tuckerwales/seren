package wales.tucker.terminal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import wales.tucker.terminal.emulator.CursorShape

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val colorSchemeId: String = "midnight",
    val fontSize: Float = 13f,
    val cursorShape: CursorShape = CursorShape.BLOCK,
    val cursorBlink: Boolean = true,
    val scrollback: Int = 5000,
    val keepScreenOn: Boolean = true,
    val vibrateOnBell: Boolean = true,
    val showExtraKeys: Boolean = true,
    val volumeKeysAsModifiers: Boolean = false,
    val appLock: Boolean = false,
    val confirmDisconnect: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val CURSOR_SHAPE = stringPreferencesKey("cursor_shape")
        val CURSOR_BLINK = booleanPreferencesKey("cursor_blink")
        val SCROLLBACK = intPreferencesKey("scrollback")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VIBRATE_ON_BELL = booleanPreferencesKey("vibrate_on_bell")
        val SHOW_EXTRA_KEYS = booleanPreferencesKey("show_extra_keys")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys_modifiers")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val CONFIRM_DISCONNECT = booleanPreferencesKey("confirm_disconnect")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: d.dynamicColor,
            colorSchemeId = p[Keys.COLOR_SCHEME] ?: d.colorSchemeId,
            fontSize = p[Keys.FONT_SIZE] ?: d.fontSize,
            cursorShape = p[Keys.CURSOR_SHAPE]?.let { runCatching { CursorShape.valueOf(it) }.getOrNull() } ?: d.cursorShape,
            cursorBlink = p[Keys.CURSOR_BLINK] ?: d.cursorBlink,
            scrollback = p[Keys.SCROLLBACK] ?: d.scrollback,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOn,
            vibrateOnBell = p[Keys.VIBRATE_ON_BELL] ?: d.vibrateOnBell,
            showExtraKeys = p[Keys.SHOW_EXTRA_KEYS] ?: d.showExtraKeys,
            volumeKeysAsModifiers = p[Keys.VOLUME_KEYS] ?: d.volumeKeysAsModifiers,
            appLock = p[Keys.APP_LOCK] ?: d.appLock,
            confirmDisconnect = p[Keys.CONFIRM_DISCONNECT] ?: d.confirmDisconnect,
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setColorScheme(id: String) = context.dataStore.edit { it[Keys.COLOR_SCHEME] = id }
    suspend fun setFontSize(v: Float) = context.dataStore.edit { it[Keys.FONT_SIZE] = v.coerceIn(MIN_FONT, MAX_FONT) }
    suspend fun setCursorShape(v: CursorShape) = context.dataStore.edit { it[Keys.CURSOR_SHAPE] = v.name }
    suspend fun setCursorBlink(v: Boolean) = context.dataStore.edit { it[Keys.CURSOR_BLINK] = v }
    suspend fun setScrollback(v: Int) = context.dataStore.edit { it[Keys.SCROLLBACK] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setVibrateOnBell(v: Boolean) = context.dataStore.edit { it[Keys.VIBRATE_ON_BELL] = v }
    suspend fun setShowExtraKeys(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_EXTRA_KEYS] = v }
    suspend fun setVolumeKeysAsModifiers(v: Boolean) = context.dataStore.edit { it[Keys.VOLUME_KEYS] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = v }
    suspend fun setConfirmDisconnect(v: Boolean) = context.dataStore.edit { it[Keys.CONFIRM_DISCONNECT] = v }

    companion object {
        const val MIN_FONT = 6f
        const val MAX_FONT = 32f
    }
}
