package wales.tucker.seren.auth.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import wales.tucker.seren.auth.backup.BackupEntry
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.core.security.SecretBox
import wales.tucker.seren.core.ui.theme.AccentColors

/** Accounts with their setup keys encrypted at rest and decrypted only in memory. */
class AccountRepository(
    private val db: AppDatabase,
    private val box: SecretBox,
    private val now: () -> Long,
) {
    private val dao = db.accounts()

    /** Decrypted keys by ciphertext, so each list update doesn't go back to the Keystore. */
    private val secrets = java.util.concurrent.ConcurrentHashMap<String, String>()

    val accounts: Flow<List<Account>> = dao.observe().map { list -> list.map(::decrypt) }.flowOn(Dispatchers.Default)

    suspend fun all(): List<Account> = withContext(Dispatchers.Default) { dao.all().map(::decrypt) }

    suspend fun get(id: Long): Account? = withContext(Dispatchers.Default) { dao.get(id)?.let(::decrypt) }

    sealed interface AddResult {
        data class Added(val id: Long) : AddResult
        data class Duplicate(val existing: Account) : AddResult
    }

    /** Adds [token] unless an account with the same setup key already exists. */
    suspend fun add(token: OtpToken, color: Int = defaultColor(token)): AddResult = withContext(Dispatchers.Default) {
        db.withTransaction {
            val existing = dao.all().map(::decrypt).firstOrNull { it.token.secret == token.secret }
            if (existing != null) {
                AddResult.Duplicate(existing)
            } else {
                AddResult.Added(dao.insert(entity(0, token, color, now())))
            }
        }
    }

    suspend fun update(id: Long, token: OtpToken, color: Int) = withContext(Dispatchers.Default) {
        val old = dao.get(id) ?: return@withContext
        dao.update(entity(id, token, color, old.created))
    }

    /** Puts back an account exactly as it was, after an undo. */
    suspend fun restore(account: Account) = withContext(Dispatchers.Default) {
        dao.insert(entity(account.id, account.token, account.color, account.created))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Moves a counter based account on to its next code. */
    suspend fun nextCounter(id: Long) = dao.incrementCounter(id)

    data class ImportSummary(val added: Int, val duplicates: Int)

    /** Adds every entry whose setup key isn't already here, in one transaction. */
    suspend fun import(entries: List<BackupEntry>): ImportSummary = withContext(Dispatchers.Default) {
        db.withTransaction {
            val known = dao.all().map { decrypt(it).token.secret }.toMutableSet()
            var added = 0
            var duplicates = 0
            val time = now()
            for (e in entries) {
                if (!known.add(e.token.secret)) {
                    duplicates++
                    continue
                }
                val color = e.color?.takeIf { it in AccentColors.indices } ?: defaultColor(e.token)
                dao.insert(entity(0, e.token, color, time))
                added++
            }
            ImportSummary(added, duplicates)
        }
    }

    /** How many of [entries] would be skipped: already here, or repeated in [entries] itself. */
    suspend fun countExisting(entries: List<BackupEntry>): Int = withContext(Dispatchers.Default) {
        val known = dao.all().map { decrypt(it).token.secret }.toMutableSet()
        entries.count { !known.add(it.token.secret) }
    }

    private fun entity(id: Long, token: OtpToken, color: Int, created: Long): AccountEntity {
        val enc = box.encryptString(token.secret)
        secrets[enc] = token.secret
        return AccountEntity(
            id = id,
            issuer = token.issuer.trim(),
            name = token.name.trim(),
            secretEnc = enc,
            type = token.type,
            algorithm = token.algorithm,
            digits = token.digits,
            period = token.period,
            counter = token.counter,
            color = color,
            created = created,
        )
    }

    private fun decrypt(e: AccountEntity): Account {
        val secret = secrets.getOrPut(e.secretEnc) { box.decryptString(e.secretEnc) }
        return Account(
            id = e.id,
            token = OtpToken(e.issuer, e.name, secret, e.type, e.algorithm, e.digits, e.period, e.counter),
            color = e.color,
            created = e.created,
        )
    }

    companion object {
        /** The same service always gets the same accent color, unless the user picks another. */
        fun defaultColor(token: OtpToken): Int = token.title.trim().lowercase().hashCode().mod(AccentColors.size)
    }
}
