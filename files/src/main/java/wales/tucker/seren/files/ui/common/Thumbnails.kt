package wales.tucker.seren.files.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.FileKind

/** Small pictures of photos and videos for file rows, made on demand and kept in memory. */
class Thumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** At most a few at once, so scrolling a folder of photos stays smooth. */
    private val gate = Semaphore(3)

    /** Remembers files that have no thumbnail, so they aren't tried again on every scroll. */
    private val failed = HashSet<String>()

    private fun key(entry: FileEntry, px: Int) = "${entry.path}:${entry.modified}:$px"

    fun cached(entry: FileEntry, px: Int): ImageBitmap? = cache.get(key(entry, px))

    suspend fun load(entry: FileEntry, px: Int): ImageBitmap? {
        val key = key(entry, px)
        cache.get(key)?.let { return it }
        synchronized(failed) { if (key in failed) return null }
        val bitmap = gate.withPermit {
            withContext(Dispatchers.IO) { runCatching { make(entry, px) }.getOrNull() }
        }?.asImageBitmap()
        if (bitmap != null) cache.put(key, bitmap) else synchronized(failed) { failed += key }
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun make(entry: FileEntry, px: Int): Bitmap? {
        val file = entry.file
        return when (entry.kind) {
            FileKind.IMAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Applies the photo's rotation, which decoding by hand would miss.
                runCatching { ThumbnailUtils.createImageThumbnail(file, Size(px, px), null) }.getOrNull() ?: decodeSampled(file.path, px)
            } else {
                decodeSampled(file.path, px)
            }
            FileKind.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
            } else {
                ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
            }
            else -> null
        }
    }

    /** Decodes [path] at the smallest power of two scale that still covers [px] square. */
    private fun decodeSampled(path: String, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
