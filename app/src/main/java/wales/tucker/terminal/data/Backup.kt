package wales.tucker.terminal.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports hosts (with their port forwards) and snippets as JSON, for moving them to
 * another device. Passwords and private keys are never included: they are bound to this device's
 * keystore. A host that used a key refers to it by name and uses it again if a key with that name
 * exists where it is imported.
 */
class Backup(private val db: AppDatabase) {

    data class ImportResult(val hosts: Int, val snippets: Int, val skipped: Int)

    suspend fun export(): String {
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
                    .put("forwards", forwards),
            )
        }
        val snippetArray = JSONArray()
        db.snippetDao().all().forEach { s ->
            snippetArray.put(JSONObject().put("name", s.name).put("command", s.command).put("autoRun", s.autoRun))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("hosts", hostArray)
            .put("snippets", snippetArray)
            .toString(2)
    }

    /**
     * Adds the hosts and snippets in [json], skipping ones that already exist.
     * @throws IllegalArgumentException if [json] is not a backup made by [export].
     */
    suspend fun import(json: String): ImportResult = db.withTransaction { importAll(json) }

    private suspend fun importAll(json: String): ImportResult {
        val root = runCatching { JSONObject(json) }.getOrNull()
        require(root != null && root.optString("format") == FORMAT) { "Not a Terminal backup file" }
        require(root.optInt("version") <= VERSION) { "This backup was made by a newer version of Terminal" }

        val keysByName = db.keyDao().all().associateBy { it.name }
        val existingHosts = db.hostDao().all()
        var skipped = 0
        var hostCount = 0
        val hosts = root.optJSONArray("hosts") ?: JSONArray()
        val newIds = arrayOfNulls<Long>(hosts.length())
        val jumpRefs = mutableMapOf<Long, Int>()
        for (i in 0 until hosts.length()) {
            val o = hosts.getJSONObject(i)
            val keyId = o.optString("keyName").takeIf { it.isNotEmpty() }?.let { keysByName[it]?.id }
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
        return ImportResult(hostCount, snippetCount, skipped)
    }

    companion object {
        const val FORMAT = "wales.tucker.terminal.backup"
        const val VERSION = 1
    }
}
