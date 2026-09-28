package wales.tucker.seren.files.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Plain JSON export/import of bookmarks. Paths are device-local absolute paths; importing on
 * another device only keeps bookmarks whose folders still exist there.
 */
object BookmarksBackup {
    private const val FORMAT = "seren-files-bookmarks"
    private const val VERSION = 1

    fun export(bookmarks: List<Bookmark>, out: OutputStream) {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put(
                "bookmarks",
                JSONArray().also { arr ->
                    bookmarks.forEach { b ->
                        arr.put(
                            JSONObject()
                                .put("path", b.path)
                                .put("name", b.name)
                                .put("added", b.added),
                        )
                    }
                },
            )
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
    }

    fun import(input: InputStream): List<Bookmark> {
        val root = JSONObject(input.readBytes().toString(Charsets.UTF_8))
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("Not a Seren Files bookmarks file")
        }
        val arr = root.getJSONArray("bookmarks")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.getString("path").trim()
                val name = o.optString("name").ifBlank { path.substringAfterLast('/') }
                if (path.isBlank()) continue
                add(Bookmark(path = path, name = name, added = o.optLong("added", System.currentTimeMillis())))
            }
        }
    }
}
