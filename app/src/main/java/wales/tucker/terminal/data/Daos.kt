package wales.tucker.terminal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY lastConnectedAt DESC, nickname COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun get(id: Long): Host?

    @Query("SELECT * FROM hosts WHERE id = :id")
    fun observe(id: Long): Flow<Host?>

    @Insert
    suspend fun insert(host: Host): Long

    @Update
    suspend fun update(host: Host)

    @Delete
    suspend fun delete(host: Host)

    @Query("UPDATE hosts SET lastConnectedAt = :time WHERE id = :id")
    suspend fun markConnected(id: Long, time: Long)

    @Query("UPDATE hosts SET jumpHostId = NULL WHERE jumpHostId = :id")
    suspend fun clearJumpHost(id: Long)

    @Query("UPDATE hosts SET keyId = NULL, authType = 'PASSWORD' WHERE keyId = :keyId")
    suspend fun clearKey(keyId: Long)
}

@Dao
interface KeyDao {
    @Query("SELECT * FROM keys ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SshKey>>

    @Query("SELECT * FROM keys WHERE id = :id")
    suspend fun get(id: Long): SshKey?

    @Insert
    suspend fun insert(key: SshKey): Long

    @Update
    suspend fun update(key: SshKey)

    @Delete
    suspend fun delete(key: SshKey)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts ORDER BY host")
    fun observeAll(): Flow<List<KnownHost>>

    @Query("SELECT * FROM known_hosts WHERE host = :host")
    fun findByHost(host: String): List<KnownHost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(knownHost: KnownHost): Long

    @Query("DELETE FROM known_hosts WHERE host = :host AND keyType = :keyType")
    fun remove(host: String, keyType: String)

    @Delete
    suspend fun delete(knownHost: KnownHost)
}

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId ORDER BY id")
    fun observeForHost(hostId: Long): Flow<List<PortForward>>

    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId ORDER BY id")
    suspend fun forHost(hostId: Long): List<PortForward>

    @Upsert
    suspend fun upsert(forward: PortForward): Long

    @Delete
    suspend fun delete(forward: PortForward)

    @Query("DELETE FROM port_forwards WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: Long)

    @Transaction
    suspend fun replaceForHost(hostId: Long, forwards: List<PortForward>) {
        deleteForHost(hostId)
        forwards.forEach { upsert(it.copy(id = 0, hostId = hostId)) }
    }
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Snippet>>

    @Upsert
    suspend fun upsert(snippet: Snippet): Long

    @Delete
    suspend fun delete(snippet: Snippet)
}
