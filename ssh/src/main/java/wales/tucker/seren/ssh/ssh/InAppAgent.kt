package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.Identity
import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Vector

/**
 * Process-memory SSH agent: holds decrypted private keys until wiped. Never persists unlocked
 * material. Implements JSch's [IdentityRepository] so agent-forwarding sign requests are answered
 * from the keys currently held.
 *
 * Unlock requires AppLock to be enabled (enforced by callers); wipe on AppLock timeout, explicit
 * lock, AppLock turned off, or process death.
 */
class InAppAgent : IdentityRepository {

    private val lock = Any()
    private val held = mutableListOf<HeldIdentity>()

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val isUnlocked: Boolean get() = _unlocked.value

    /** How many keys are currently held (0 when locked). */
    val keyCount: Int get() = synchronized(lock) { held.size }

    /**
     * Loads [keys] (name to unencrypted OpenSSH private key bytes) into memory, replacing any
     * previously held identities. Empty [keys] still marks the agent unlocked so ForwardAgent can
     * be enabled with no keys stored yet.
     */
    fun unlock(keys: List<LoadedKey>) {
        synchronized(lock) {
            clearHeld()
            val jsch = JSch()
            for (key in keys) {
                val kp = try {
                    KeyPair.load(jsch, key.privateKey, null)
                } catch (_: Exception) {
                    continue
                }
                if (kp.isEncrypted) {
                    kp.dispose()
                    continue
                }
                held += HeldIdentity(key.name, kp)
            }
            _unlocked.value = true
        }
    }

    /** Clears all held key material. Safe to call when already locked. */
    fun wipe() {
        synchronized(lock) {
            clearHeld()
            _unlocked.value = false
        }
    }

    private fun clearHeld() {
        held.forEach { it.clear() }
        held.clear()
    }

    override fun getName(): String = "Seren in-app agent"

    override fun getStatus(): Int =
        if (isUnlocked) IdentityRepository.RUNNING else IdentityRepository.NOTRUNNING

    override fun getIdentities(): Vector<Identity> = synchronized(lock) {
        Vector<Identity>(held.size).also { v -> held.forEach { v.addElement(it) } }
    }

    /** Remote add-identity is not supported; keys are loaded only via [unlock]. */
    override fun add(identity: ByteArray?): Boolean = false

    override fun remove(blob: ByteArray?): Boolean {
        if (blob == null) return false
        synchronized(lock) {
            val index = held.indexOfFirst { id ->
                val b = id.publicKeyBlob ?: return@indexOfFirst false
                b.contentEquals(blob)
            }
            if (index < 0) return false
            held.removeAt(index).clear()
            return true
        }
    }

    override fun removeAll() {
        wipe()
    }

    data class LoadedKey(val name: String, val privateKey: ByteArray)
}

/** [Identity] backed by an in-memory [KeyPair]; cleared on [clear]/wipe. */
private class HeldIdentity(
    private val name: String,
    private var keyPair: KeyPair?,
) : Identity {
    override fun setPassphrase(passphrase: ByteArray?): Boolean {
        val kp = keyPair ?: return false
        return !kp.isEncrypted || kp.decrypt(passphrase)
    }

    override fun getPublicKeyBlob(): ByteArray? = keyPair?.publicKeyBlob

    override fun getSignature(data: ByteArray): ByteArray? = keyPair?.getSignature(data)

    override fun getSignature(data: ByteArray, alg: String): ByteArray? = keyPair?.getSignature(data, alg)

    override fun getAlgName(): String = keyPair?.keyTypeString ?: ""

    override fun getName(): String = name

    override fun isEncrypted(): Boolean = keyPair?.isEncrypted == true

    override fun clear() {
        keyPair?.dispose()
        keyPair = null
    }
}
