package wales.tucker.seren.files.ui.common

import wales.tucker.seren.files.fs.Volume
import wales.tucker.seren.files.fs.volumeFor
import java.io.File

/**
 * Where [file] is, the way people read it: "/Download/Photos" on internal storage, "SD card/Photos"
 * on another volume, or the full path when it's on none.
 */
fun displayPath(file: File, volumes: List<Volume>): String {
    val volume = volumeFor(file, volumes) ?: return file.path
    val relative = file.path.removePrefix(volume.root.path).trimStart('/')
    return if (volume.primary) "/$relative" else if (relative.isEmpty()) volume.name else "${volume.name}/$relative"
}

/** The name to show for a folder: a volume's name at its root, otherwise the folder's own name. */
fun folderTitle(folder: File, volumes: List<Volume>): String =
    volumes.firstOrNull { it.root.path == folder.path }?.name ?: folder.name.ifEmpty { folder.path }
