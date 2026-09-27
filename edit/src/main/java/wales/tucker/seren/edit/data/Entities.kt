package wales.tucker.seren.edit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A file opened or created in Seren Edit, for the Recent tab. */
@Entity(tableName = "recent_files")
data class RecentFile(
    /** The document's content URI; Seren Edit keeps a persistable grant to it where it can. */
    @PrimaryKey val uri: String,
    val name: String,
    /** Where the file lives, for display, such as "Internal storage/Documents". */
    val location: String,
    /** Index into the accent palette. */
    val color: Int,
    val lastOpened: Long,
)

/** A folder the user gave Seren Edit access to, browsable from the Files tab. */
@Entity(tableName = "folders")
data class Folder(
    /** The tree URI returned by the system folder picker. */
    @PrimaryKey val uri: String,
    val name: String,
    val location: String,
    val color: Int,
    val added: Long,
)
