package wales.tucker.seren.ssh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun authTypeToString(v: AuthType): String = v.name
    @TypeConverter fun stringToAuthType(v: String): AuthType = AuthType.valueOf(v)
    @TypeConverter fun keyTypeToString(v: KeyType): String = v.name
    @TypeConverter fun stringToKeyType(v: String): KeyType = KeyType.valueOf(v)
    @TypeConverter fun forwardTypeToString(v: ForwardType): String = v.name
    @TypeConverter fun stringToForwardType(v: String): ForwardType = ForwardType.valueOf(v)
}

@Database(
    entities = [Host::class, SshKey::class, KnownHost::class, PortForward::class, Snippet::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun keyDao(): KeyDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun snippetDao(): SnippetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN forwardAgent INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "terminal.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
