package wales.tucker.seren.ssh.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AuthType { PASSWORD, KEY, NONE }

@Entity(tableName = "hosts")
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    /** Password encrypted with [wales.tucker.seren.core.security.SecretBox], or null to prompt. */
    val encryptedPassword: String? = null,
    val keyId: Long? = null,
    val color: Int = 0,
    val group: String = "",
    val startupCommand: String = "",
    val jumpHostId: Long? = null,
    val keepAliveSeconds: Int = 30,
    val compression: Boolean = false,
    /**
     * When true, the shell requests SSH agent forwarding so the remote can use keys currently
     * unlocked in the in-app agent. Requires AppLock; the agent is wiped when the app locks.
     */
    val forwardAgent: Boolean = false,
    val colorSchemeId: String? = null,
    val lastConnectedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayName: String get() = nickname.ifBlank { hostname }
    val address: String get() = if (port == 22) "$username@$hostname" else "$username@$hostname:$port"
}

enum class KeyType(val label: String) {
    ED25519("Ed25519"),
    ECDSA("ECDSA"),
    RSA("RSA"),
    OTHER("Other"),
}

@Entity(tableName = "keys")
data class SshKey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: KeyType,
    val bits: Int,
    /** OpenSSH private key (unencrypted), itself encrypted with SecretBox. */
    val encryptedPrivateKey: String,
    /** Public key in authorized_keys format. */
    val publicKey: String,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "known_hosts", indices = [Index(value = ["host", "keyType"], unique = true)])
data class KnownHost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Host as JSch reports it: "hostname" for port 22 or "[hostname]:port". */
    val host: String,
    val keyType: String,
    /** Base64 encoded public key blob. */
    val key: String,
    val fingerprint: String,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class ForwardType { LOCAL, REMOTE, DYNAMIC }

@Entity(
    tableName = "port_forwards",
    foreignKeys = [ForeignKey(entity = Host::class, parentColumns = ["id"], childColumns = ["hostId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("hostId")],
)
data class PortForward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: Long,
    val type: ForwardType,
    val bindAddress: String = "127.0.0.1",
    val sourcePort: Int,
    val destHost: String = "localhost",
    val destPort: Int = 0,
    val enabled: Boolean = true,
) {
    val summary: String
        get() = when (type) {
            ForwardType.LOCAL -> "L $sourcePort → $destHost:$destPort"
            ForwardType.REMOTE -> "R $sourcePort → $destHost:$destPort"
            ForwardType.DYNAMIC -> "D $sourcePort (SOCKS)"
        }
}

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val command: String,
    /** Send a newline after the command. */
    val autoRun: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
