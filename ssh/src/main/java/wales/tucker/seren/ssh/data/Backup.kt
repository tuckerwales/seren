package wales.tucker.seren.ssh.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import wales.tucker.seren.core.backup.PasswordSeal
import wales.tucker.seren.core.security.SecretBox

/**
 * Exports and imports hosts (with their port forwards) and snippets as JSON, for moving them to
 * another device.
 *
 * Without a password, saved passwords and private keys are never included. A host that used a key
 * refers to it by name and uses it again if a key with that name exists where it is imported.
 *
 * With a password, the file also carries saved passwords and private keys, and everything in it is
 * encrypted with that password the way every Seren backup is ([PasswordSeal]). [secretBox] is
 * needed for that, to read them here and store them again on import.
 */
class Backup(private val db: AppDatabase, private val secretBox: SecretBox? = null) {

    data class ImportResult(val hosts: Int, val snippets: Int, val skipped: Int, val keys: Int = 0)

    /** The backup is encrypted and [import] needs its password. */
    class PasswordRequiredException : IllegalArgumentException("This backup is protected with a password")

    class WrongPasswordException : IllegalArgumentException("That password isn't right")

    /** Writes a backup; with a [password] it includes saved passwords and private keys. */
    suspend fun export(password: CharArray? = null): String {
        val withSecrets = password != null
        val box = if (withSecrets) requireNotNull(secretBox) { "Exporting secrets needs the SecretBox" } else null
        val hosts = db.hostDao().all()
        val keys = db.keyDao().all().associateBy { it.id }
        val index = hosts.withIndex().associate { (i, h) -> h.id to i }
        val hostArray = JSONArray()
        for (h in hosts) {
            val forwards = JSONArray()
            db.portForwardDao().forHost(h.id).forEach { f ->
                forwards.put(
                    JSONObject()
                        .put("type", f.type.name)
                        .put("bindAddress", f.bindAddress)
                        .put("sourcePort", f.sourcePort)
                        .put("destHost", f.destHost)
                        .put("destPort", f.destPort)
                        .put("enabled", f.enabled),
                )
            }
            hostArray.put(
                JSONObject()
                    .put("nickname", h.nickname)
                    .put("hostname", h.hostname)
                    .put("port", h.port)
                    .put("username", h.username)
                    .put("authType", h.authType.name)
                    .putOpt("keyName", h.keyId?.let { keys[it]?.name })
                    .put("color", h.color)
                    .put("group", h.group)
                    .put("startupCommand", h.startupCommand)
                    // Jump hosts are referenced by their position in this list.
                    .putOpt("jumpHost", h.jumpHostId?.let { index[it] })
                    .put("keepAliveSeconds", h.keepAliveSeconds)
                    .put("compression", h.compression)
                    .putOpt("colorSchemeId", h.colorSchemeId)
                    .putOpt("password", h.encryptedPassword?.let { box?.decryptString(it) })
                    .put("forwards", forwards),
            )
        }
        val snippetArray = JSONArray()
        db.snippetDao().all().forEach { s ->
            snippetArray.put(JSONObject().put("name", s.name).put("command", s.command).put("autoRun", s.autoRun))
        }
        val root = JSONObject().put("format", FORMAT)
        if (box == null) {
            return root.put("version", 1).put("hosts", hostArray).put("snippets", snippetArray).toString(2)
        }
        val keyArray = JSONArray()
        keys.values.forEach { k ->
            keyArray.put(
                JSONObject()
                    .put("name", k.name)
                    .put("type", k.type.name)
                    .put("bits", k.bits)
                    .put("privateKey", box.decryptString(k.encryptedPrivateKey))
                    .put("publicKey", k.publicKey)
                    .put("fingerprint", k.fingerprint),
            )
        }
        val content = JSONObject().put("hosts", hostArray).put("snippets", snippetArray).put("keys", keyArray)
        return PasswordSeal.seal(root.put("version", VERSION), content.toString().toByteArray(Charsets.UTF_8), password!!).toString(2)
    }

    /** Whether [json] is a password protected backup, so the password can be asked for first. */
    fun isEncrypted(json: String): Boolean = runCatching { PasswordSeal.isSealed(JSONObject(json)) }.getOrDefault(false)

    /**
     * Adds the keys, hosts and snippets in [json], skipping ones that already exist.
     * @throws IllegalArgumentException if [json] is not a backup made by [export], with
     * [PasswordRequiredException] or [WrongPasswordException] when it is encrypted.
     */
    suspend fun import(json: String, password: CharArray? = null): ImportResult {
        val content = open(json, password)
        return db.withTransaction { importAll(content) }
    }

    /** The backup's contents, decrypted when need be. */
    private fun open(json: String, password: CharArray?): JSONObject {
        val root = runCatching { JSONObject(json) }.getOrNull()
        require(root != null && root.optString("format") in FORMATS) { "Not a Seren SSH backup file" }
        require(root.optInt("version") <= VERSION) { "This backup was made by a newer version of Seren SSH" }
        if (!PasswordSeal.isSealed(root)) return root
        val sealed = try {
            PasswordSeal.read(root)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This backup is damaged")
        }
        if (password == null) throw PasswordRequiredException()
        requireNotNull(secretBox) { "Importing secrets needs the SecretBox" }
        val plain = sealed.open(password) ?: throw WrongPasswordException()
        return try {
            JSONObject(plain.toString(Charsets.UTF_8))
        } catch (e: JSONException) {
            throw IllegalArgumentException("This backup is damaged")
        }
    }

    private suspend fun importAll(root: JSONObject): ImportResult {
        val existingKeys = db.keyDao().all()
        // Keys first, so hosts can use them. A key already here (same fingerprint) is reused.
        val keysByName = existingKeys.associate { it.name to it.id }.toMutableMap()
        var keyCount = 0
        var skipped = 0
        val keyArray = root.optJSONArray("keys") ?: JSONArray()
        for (i in 0 until keyArray.length()) {
            val o = keyArray.getJSONObject(i)
            val name = o.getString("name")
            val fingerprint = o.getString("fingerprint")
            val existing = existingKeys.firstOrNull { it.fingerprint == fingerprint }
            if (existing != null) {
                keysByName[name] = existing.id
                skipped++
                continue
            }
            val box = secretBox ?: continue
            val id = db.keyDao().insert(
                SshKey(
                    name = uniqueName(name, keysByName.keys),
                    type = runCatching { KeyType.valueOf(o.optString("type")) }.getOrDefault(KeyType.OTHER),
                    bits = o.optInt("bits"),
                    encryptedPrivateKey = box.encryptString(o.getString("privateKey")),
                    publicKey = o.optString("publicKey"),
                    fingerprint = fingerprint,
                ),
            )
            keysByName[name] = id
            keyCount++
        }

        val existingHosts = db.hostDao().all()
        var hostCount = 0
        val hosts = root.optJSONArray("hosts") ?: JSONArray()
        val newIds = arrayOfNulls<Long>(hosts.length())
        val jumpRefs = mutableMapOf<Long, Int>()
        for (i in 0 until hosts.length()) {
            val o = hosts.getJSONObject(i)
            val keyId = o.optString("keyName").takeIf { it.isNotEmpty() }?.let { keysByName[it] }
            val authType = runCatching { AuthType.valueOf(o.optString("authType")) }.getOrDefault(AuthType.PASSWORD)
            val host = Host(
                nickname = o.optString("nickname"),
                hostname = o.getString("hostname"),
                port = o.optInt("port", 22),
                username = o.getString("username"),
                // A key that is not on this device can't be used; ask for credentials instead.
                authType = if (authType == AuthType.KEY && keyId == null) AuthType.NONE else authType,
                keyId = if (authType == AuthType.KEY) keyId else null,
                color = o.optInt("color"),
                group = o.optString("group"),
                startupCommand = o.optString("startupCommand"),
                keepAliveSeconds = o.optInt("keepAliveSeconds", 30),
                compression = o.optBoolean("compression"),
                colorSchemeId = o.optString("colorSchemeId").takeIf { it.isNotEmpty() },
                encryptedPassword = o.optString("password").takeIf { it.isNotEmpty() }?.let { secretBox?.encryptString(it) },
            )
            val duplicate = existingHosts.firstOrNull {
                it.nickname == host.nickname && it.hostname == host.hostname && it.port == host.port && it.username == host.username
            }
            if (duplicate != null) {
                newIds[i] = duplicate.id
                skipped++
                continue
            }
            val id = db.hostDao().insert(host)
            newIds[i] = id
            hostCount++
            if (o.has("jumpHost")) jumpRefs[id] = o.getInt("jumpHost")
            val forwards = o.optJSONArray("forwards") ?: JSONArray()
            val list = (0 until forwards.length()).mapNotNull { j ->
                val f = forwards.getJSONObject(j)
                val type = runCatching { ForwardType.valueOf(f.optString("type")) }.getOrNull() ?: return@mapNotNull null
                PortForward(
                    hostId = id,
                    type = type,
                    bindAddress = f.optString("bindAddress", "127.0.0.1"),
                    sourcePort = f.getInt("sourcePort"),
                    destHost = f.optString("destHost"),
                    destPort = f.optInt("destPort"),
                    enabled = f.optBoolean("enabled", true),
                )
            }
            db.portForwardDao().replaceForHost(id, list)
        }
        for ((id, ref) in jumpRefs) {
            val jumpId = newIds.getOrNull(ref) ?: continue
            if (jumpId == id) continue
            db.hostDao().get(id)?.let { db.hostDao().update(it.copy(jumpHostId = jumpId)) }
        }

        val existingSnippets = db.snippetDao().all()
        var snippetCount = 0
        val snippets = root.optJSONArray("snippets") ?: JSONArray()
        for (i in 0 until snippets.length()) {
            val o = snippets.getJSONObject(i)
            val snippet = Snippet(name = o.optString("name"), command = o.getString("command"), autoRun = o.optBoolean("autoRun", true))
            if (existingSnippets.any { it.name == snippet.name && it.command == snippet.command }) {
                skipped++
                continue
            }
            db.snippetDao().upsert(snippet)
            snippetCount++
        }
        return ImportResult(hostCount, snippetCount, skipped, keyCount)
    }

    /** [name], or "name (2)" and so on when a different key already has it. */
    private fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        var n = 2
        while ("$name ($n)" in taken) n++
        return "$name ($n)"
    }

    companion object {
        const val FORMAT = "wales.tucker.seren.ssh.backup"

        /** [FORMAT] plus the id used before the app was renamed from Terminal to Seren SSH. */
        private val FORMATS = setOf(FORMAT, "wales.tucker.terminal.backup")

        /** 2 added password protected backups with keys; plain ones are still written as 1. */
        const val VERSION = 2
    }
}
