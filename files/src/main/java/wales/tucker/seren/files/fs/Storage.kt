package wales.tucker.seren.files.fs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A place files are kept: the device's internal storage or an SD card or USB drive. */
data class Volume(
    val name: String,
    val root: File,
    val primary: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

/** The volume [file] is on, or null if it's outside them all. */
fun volumeFor(file: File, volumes: List<Volume>): Volume? =
    volumes.filter { FileOps.isInside(file, it.root) }.maxByOrNull { it.root.path.length }

/** The device's storage, behind an interface so tests can point Seren Files at a folder. */
interface Storage {
    /** Whether Seren Files may read and write shared storage. */
    fun hasAccess(): Boolean

    /** Every mounted volume, internal storage first. */
    fun volumes(): List<Volume>

    /** Files changed at or after [since], newest first. */
    suspend fun recent(since: Long, limit: Int): List<FileEntry>

    /** Every file on every volume, except hidden ones, for finding them by category. */
    suspend fun allFiles(): List<FileEntry> = Search.allFiles(volumes().map { it.root })
}

/** The folders people look for first, relative to internal storage, in the order they're shown. */
val QuickFolders = listOf(
    "Download" to "Downloads",
    "Documents" to "Documents",
    "DCIM" to "Camera",
    "Pictures" to "Pictures",
    "Music" to "Music",
    "Movies" to "Movies",
)

class AndroidStorage(private val context: Context) : Storage {

    override fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    override fun volumes(): List<Volume> {
        val manager = context.getSystemService(StorageManager::class.java)
        // Each volume's app folder is <root>/Android/data/<package>/files, which gives its root on
        // every Android version without needing hidden APIs.
        val roots = context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
            val path = dir.path
            val cut = path.indexOf("/Android/data/")
            if (cut > 0) File(path.substring(0, cut)) else null
        }.ifEmpty { listOf(Environment.getExternalStorageDirectory()) }
        return roots.distinctBy { it.path }.mapIndexedNotNull { i, root ->
            if (Environment.getExternalStorageState(root) != Environment.MEDIA_MOUNTED) return@mapIndexedNotNull null
            val stat = runCatching { StatFs(root.path) }.getOrNull()
            val name = if (i == 0) {
                "Internal storage"
            } else {
                manager?.getStorageVolume(root)?.getDescription(context) ?: root.name
            }
            Volume(name, root, primary = i == 0, totalBytes = stat?.totalBytes ?: 0, freeBytes = stat?.availableBytes ?: 0)
        }
    }

    override suspend fun recent(since: Long, limit: Int): List<FileEntry> = withContext(Dispatchers.IO) {
        fromMediaStore(since, limit) ?: Search.recentlyChanged(volumes().map { it.root }, since, limit)
    }

    override suspend fun allFiles(): List<FileEntry> = withContext(Dispatchers.IO) {
        allFromMediaStore() ?: Search.allFiles(volumes().map { it.root })
    }

    /**
     * Every file the media store knows, from its own record of each file's size and date, so
     * nothing has to be read from storage. Files it lists that have since gone are dropped when a
     * category is shown.
     */
    @Suppress("DEPRECATION") // DATA is the only way to get a path, and works with all files access.
    private fun allFromMediaStore(): List<FileEntry>? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
        // Folders have no type; large files of a type Android doesn't know still count.
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL OR ${MediaStore.MediaColumns.SIZE} >= ?"
        val cursor = runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                arrayOf(Categories.LARGE_BYTES.toString()),
                null,
            )
        }.getOrNull() ?: return null
        val found = mutableListOf<FileEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val path = it.getString(0) ?: continue
                if (path.contains("/.")) continue
                found += FileEntry(path, path.substringAfterLast('/'), isDirectory = false, size = it.getLong(1), modified = it.getLong(2) * 1000)
            }
        }
        return found
    }

    /** The media store indexes every file on shared storage, so asking it is far quicker than walking. */
    @Suppress("DEPRECATION") // DATA is the only way to get a path, and works with all files access.
    private fun fromMediaStore(since: Long, limit: Int): List<FileEntry>? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} >= ? AND ${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL"
        val cursor = runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                arrayOf((since / 1000).toString()),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return null
        val found = mutableListOf<FileEntry>()
        cursor.use {
            while (it.moveToNext() && found.size < limit) {
                val path = it.getString(0) ?: continue
                if (path.contains("/.")) continue
                val file = File(path)
                if (file.isFile) found += FileEntry.of(file)
            }
        }
        return found.sortedByDescending { it.modified }
    }

    companion object {
        /** The system screen where people allow all files access, or null before Android 11. */
        fun accessSettingsIntent(context: Context): Intent? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
        }

        /** Fallback for devices without the per-app screen. */
        fun allAccessSettingsIntent(): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) else null
    }
}
