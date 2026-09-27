package wales.tucker.seren.files.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY name COLLATE NOCASE, path")
    fun observe(): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    suspend fun isBookmarked(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun delete(path: String)

    /** Forgets the bookmark for [path] and any for folders inside it. */
    @Query("DELETE FROM bookmarks WHERE path = :path OR substr(path, 1, length(:path) + 1) = :path || '/'")
    suspend fun deleteUnder(path: String)

    @Query("UPDATE bookmarks SET name = :newName WHERE path = :path")
    suspend fun rename(path: String, newName: String)

    @Query(
        "UPDATE bookmarks SET path = :newPath || substr(path, length(:oldPath) + 1) " +
            "WHERE path = :oldPath OR substr(path, 1, length(:oldPath) + 1) = :oldPath || '/'",
    )
    suspend fun movePaths(oldPath: String, newPath: String)

    /** Keeps bookmarks pointing at a folder (or folders inside it) after it's renamed or moved. */
    @Transaction
    suspend fun moveUnder(oldPath: String, newPath: String, newName: String) {
        movePaths(oldPath, newPath)
        rename(newPath, newName)
    }
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC, id DESC")
    fun observe(): Flow<List<TrashItem>>

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC, id DESC")
    suspend fun all(): List<TrashItem>

    @Query("SELECT * FROM trash WHERE deletedAt < :before")
    suspend fun olderThan(before: Long): List<TrashItem>

    @Insert
    suspend fun insert(item: TrashItem): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)
}
