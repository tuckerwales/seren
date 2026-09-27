package wales.tucker.seren.edit.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT $MAX_RECENT_FILES")
    fun observe(): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent_files WHERE uri = :uri")
    suspend fun get(uri: String): RecentFile?

    @Upsert
    suspend fun upsert(file: RecentFile)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun delete(uri: String)

    /** Files beyond the [MAX_RECENT_FILES] most recently opened, which the list no longer shows. */
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT -1 OFFSET $MAX_RECENT_FILES")
    suspend fun overflow(): List<RecentFile>

    companion object {
        const val MAX_RECENT_FILES = 50
    }
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE uri = :uri")
    suspend fun get(uri: String): Folder?

    @Upsert
    suspend fun upsert(folder: Folder)

    @Query("DELETE FROM folders WHERE uri = :uri")
    suspend fun delete(uri: String)
}
