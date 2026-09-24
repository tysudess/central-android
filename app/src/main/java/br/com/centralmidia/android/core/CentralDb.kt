package br.com.centralmidia.android.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

class CentralDb(
    private val appContext: Context,
) : SQLiteOpenHelper(appContext, "central_android.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "type TEXT," +
                "title TEXT," +
                "detail TEXT," +
                "url TEXT)"
        )
        db.execSQL(
            "CREATE TABLE demands(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle TEXT NOT NULL," +
                "subject TEXT NOT NULL," +
                "active INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL(
            "CREATE TABLE terms(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "term TEXT NOT NULL UNIQUE," +
                "active INTEGER NOT NULL DEFAULT 1)"
        )
        createVideoTerms(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createVideoTerms(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        seedDefaultTerms(db)
    }

    private fun createVideoTerms(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS video_terms(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "term TEXT NOT NULL UNIQUE," +
                "active INTEGER NOT NULL DEFAULT 1)"
        )
    }

    private fun seedDefaultTerms(db: SQLiteDatabase) {
        val defaults = runCatching {
            val text = appContext.assets
                .open("default_terms.json")
                .bufferedReader()
                .use { it.readText() }
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val term = arr.optString(i).trim()
                    if (term.isNotBlank()) add(term)
                }
            }
        }.getOrElse { emptyList() }

        if (defaults.isEmpty()) return

        fun count(table: String): Int = db.rawQuery(
            "SELECT COUNT(*) FROM $table WHERE active=1",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        fun insertTerms(table: String, values: Iterable<String>) {
            values.forEach { term ->
                db.insertWithOnConflict(
                    table,
                    null,
                    ContentValues().apply {
                        put("term", term)
                        put("active", 1)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }

        if (count("terms") == 0) {
            insertTerms("terms", defaults)
        }

        if (count("video_terms") == 0) {
            val newsTerms = buildList {
                db.rawQuery(
                    "SELECT term FROM terms WHERE active=1 ORDER BY term COLLATE NOCASE",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val term = cursor.getString(0).orEmpty().trim()
                        if (term.isNotBlank()) add(term)
                    }
                }
            }
            insertTerms("video_terms", (newsTerms + defaults).distinctBy { it.lowercase() })
        }
    }

    fun addHistory(
        type: String,
        title: String,
        detail: String = "",
        url: String = "",
    ) {
        writableDatabase.insert(
            "history",
            null,
            ContentValues().apply {
                put("ts", System.currentTimeMillis())
                put("type", type)
                put("title", title)
                put("detail", detail)
                put("url", url)
            },
        )
    }

    fun addHistoryUnique(
        type: String,
        title: String,
        detail: String = "",
        url: String = "",
    ) {
        if (url.isBlank()) {
            addHistory(type, title, detail, url)
            return
        }

        val db = writableDatabase
        val values = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("type", type)
            put("title", title)
            put("detail", detail)
            put("url", url)
        }

        val updated = db.update(
            "history",
            values,
            "type=? AND url=?",
            arrayOf(type, url),
        )

        if (updated == 0) {
            db.insert("history", null, values)
        }
    }

    fun history(
        limit: Int = 250,
        type: String? = null,
    ): List<Array<String>> {
        val out = mutableListOf<Array<String>>()
        val sql: String
        val args: Array<String>

        if (type.isNullOrBlank()) {
            sql =
                "SELECT id,ts,type,title,detail,url FROM history " +
                    "ORDER BY ts DESC LIMIT ?"
            args = arrayOf(limit.toString())
        } else {
            sql =
                "SELECT id,ts,type,title,detail,url FROM history " +
                    "WHERE type=? ORDER BY ts DESC LIMIT ?"
            args = arrayOf(type, limit.toString())
        }

        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += arrayOf(
                    c.getLong(0).toString(),
                    c.getLong(1).toString(),
                    c.getString(2).orEmpty(),
                    c.getString(3).orEmpty(),
                    c.getString(4).orEmpty(),
                    c.getString(5).orEmpty(),
                )
            }
        }
        return out
    }

    fun clearHistory(type: String? = null) {
        if (type.isNullOrBlank()) {
            writableDatabase.delete("history", null, null)
        } else {
            writableDatabase.delete("history", "type=?", arrayOf(type))
        }
    }

    fun addDemand(vehicle: String, subject: String) {
        writableDatabase.insert(
            "demands",
            null,
            ContentValues().apply {
                put("vehicle", vehicle.trim())
                put("subject", subject.trim())
                put("active", 1)
            },
        )
    }

    fun demands(): List<Triple<Long, String, String>> {
        val out = mutableListOf<Triple<Long, String, String>>()
        readableDatabase.rawQuery(
            "SELECT id,vehicle,subject FROM demands " +
                "WHERE active=1 ORDER BY id DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += Triple(
                    c.getLong(0),
                    c.getString(1),
                    c.getString(2),
                )
            }
        }
        return out
    }

    fun deleteDemand(id: Long) {
        writableDatabase.delete(
            "demands",
            "id=?",
            arrayOf(id.toString()),
        )
    }

    fun addTerm(
        term: String,
        kind: String = "news",
    ) {
        val table = termTable(kind)
        runCatching {
            writableDatabase.insertOrThrow(
                table,
                null,
                ContentValues().apply {
                    put("term", term.trim())
                    put("active", 1)
                },
            )
        }
    }

    fun terms(
        kind: String = "news",
    ): List<Pair<Long, String>> {
        val table = termTable(kind)
        val out = mutableListOf<Pair<Long, String>>()
        readableDatabase.rawQuery(
            "SELECT id,term FROM $table WHERE active=1 " +
                "ORDER BY term COLLATE NOCASE",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += c.getLong(0) to c.getString(1)
            }
        }
        return out
    }

    fun deleteTerm(
        id: Long,
        kind: String = "news",
    ) {
        writableDatabase.delete(
            termTable(kind),
            "id=?",
            arrayOf(id.toString()),
        )
    }

    private fun termTable(kind: String): String =
        if (kind.equals("video", ignoreCase = true)) {
            "video_terms"
        } else {
            "terms"
        }
}
