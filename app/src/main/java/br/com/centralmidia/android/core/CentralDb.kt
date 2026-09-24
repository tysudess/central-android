package br.com.centralmidia.android.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CentralDb(context: Context) : SQLiteOpenHelper(context, "central_android.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, type TEXT, title TEXT, detail TEXT, url TEXT)")
        db.execSQL("CREATE TABLE demands(id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle TEXT NOT NULL, subject TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE terms(id INTEGER PRIMARY KEY AUTOINCREMENT, term TEXT NOT NULL UNIQUE, active INTEGER NOT NULL DEFAULT 1)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun addHistory(type:String, title:String, detail:String="", url:String="") {
        writableDatabase.insert("history", null, ContentValues().apply {
            put("ts", System.currentTimeMillis()); put("type", type); put("title", title); put("detail", detail); put("url", url)
        })
    }

    fun history(limit:Int=250): List<Array<String>> {
        val out=mutableListOf<Array<String>>()
        readableDatabase.rawQuery("SELECT id,ts,type,title,detail,url FROM history ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) out += arrayOf(c.getLong(0).toString(),c.getLong(1).toString(),c.getString(2).orEmpty(),c.getString(3).orEmpty(),c.getString(4).orEmpty(),c.getString(5).orEmpty())
        }
        return out
    }
    fun clearHistory() { writableDatabase.delete("history", null, null) }

    fun addDemand(vehicle:String, subject:String) { writableDatabase.insert("demands",null,ContentValues().apply { put("vehicle",vehicle);put("subject",subject);put("active",1) }) }
    fun demands(): List<Triple<Long,String,String>> { val out=mutableListOf<Triple<Long,String,String>>(); readableDatabase.rawQuery("SELECT id,vehicle,subject FROM demands WHERE active=1 ORDER BY id DESC",null).use { c->while(c.moveToNext()) out+=Triple(c.getLong(0),c.getString(1),c.getString(2)) }; return out }
    fun deleteDemand(id:Long){ writableDatabase.delete("demands","id=?",arrayOf(id.toString())) }

    fun addTerm(term:String){ runCatching { writableDatabase.insertOrThrow("terms",null,ContentValues().apply { put("term",term.trim());put("active",1) }) } }
    fun terms(): List<Pair<Long,String>> { val out=mutableListOf<Pair<Long,String>>(); readableDatabase.rawQuery("SELECT id,term FROM terms WHERE active=1 ORDER BY term",null).use { c->while(c.moveToNext()) out+=c.getLong(0) to c.getString(1) }; return out }
    fun deleteTerm(id:Long){ writableDatabase.delete("terms","id=?",arrayOf(id.toString())) }
}
