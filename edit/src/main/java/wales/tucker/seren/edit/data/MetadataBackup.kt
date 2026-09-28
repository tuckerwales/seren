package wales.tucker.seren.edit.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Export/import of recent-files and folder metadata. SAF grants stay on the device that created
 * them — importing elsewhere restores names and URIs, but Android may still require re-picking
 * folders that have no persistable grant on this device.
 */
object MetadataBackup {
    private const val FORMAT = "seren-edit-metadata"
    private const val VERSION = 1

    fun export(recents: List<RecentFile>, folders: List<Folder>, out: OutputStream) {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put(
                "recents",
                JSONArray().also { arr ->
                    recents.forEach { r ->
                        arr.put(
                            JSONObject()
                                .put("uri", r.uri)
                                .put("name", r.name)
                                .put("location", r.location)
                                .put("color", r.color)
                                .put("lastOpened", r.lastOpened),
                        )
                    }
                },
            )
            .put(
                "folders",
                JSONArray().also { arr ->
                    folders.forEach { f ->
                        arr.put(
                            JSONObject()
                                .put("uri", f.uri)
                                .put("name", f.name)
                                .put("location", f.location)
                                .put("color", f.color)
                                .put("added", f.added),
                        )
                    }
                },
            )
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
    }

    data class Imported(val recents: List<RecentFile>, val folders: List<Folder>)

    fun import(input: InputStream): Imported {
        val root = JSONObject(input.readBytes().toString(Charsets.UTF_8))
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("Not a Seren Edit metadata file")
        }
        val recents = buildList {
            val arr = root.optJSONArray("recents") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RecentFile(
                        uri = o.getString("uri"),
                        name = o.optString("name").ifBlank { "file" },
                        location = o.optString("location"),
                        color = o.optInt("color"),
                        lastOpened = o.optLong("lastOpened", System.currentTimeMillis()),
                    ),
                )
            }
        }
        val folders = buildList {
            val arr = root.optJSONArray("folders") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Folder(
                        uri = o.getString("uri"),
                        name = o.optString("name").ifBlank { "Folder" },
                        location = o.optString("location"),
                        color = o.optInt("color"),
                        added = o.optLong("added", System.currentTimeMillis()),
                    ),
                )
            }
        }
        return Imported(recents, folders)
    }
}
