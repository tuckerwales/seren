package wales.tucker.seren.auth.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY CASE WHEN issuer = '' THEN name ELSE issuer END COLLATE NOCASE, name COLLATE NOCASE, id")
    fun observe(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY CASE WHEN issuer = '' THEN name ELSE issuer END COLLATE NOCASE, name COLLATE NOCASE, id")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET counter = counter + 1 WHERE id = :id")
    suspend fun incrementCounter(id: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}
