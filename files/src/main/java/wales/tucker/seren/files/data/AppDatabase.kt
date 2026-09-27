package wales.tucker.seren.files.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Bookmark::class, TrashItem::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarks(): BookmarkDao
    abstract fun trash(): TrashDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "seren-files.db").build()
    }
}
