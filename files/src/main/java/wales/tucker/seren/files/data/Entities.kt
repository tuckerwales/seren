package wales.tucker.seren.files.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

/** A folder pinned to the Browse tab. */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val path: String,
    val name: String,
    val added: Long,
)

/** Something in the trash: where it came from and where it's kept until it's restored or deleted. */
@Entity(tableName = "trash")
data class TrashItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originalPath: String,
    /** Where it's kept, in the trash folder at the root of the same volume. */
    val storedPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val deletedAt: Long,
) {
    val originalFolder: String get() = File(originalPath).parent.orEmpty()
}
