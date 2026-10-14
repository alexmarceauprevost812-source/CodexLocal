package com.codexlocal.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Mémoire locale V3 : aucune connexion réseau, aucun accès aux poids GGUF. */
class MarceauMemory(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "marceau_memory_v3.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE memories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category TEXT NOT NULL,
            project TEXT NOT NULL DEFAULT '',
            content TEXT NOT NULL,
            verified INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            project TEXT NOT NULL,
            objective TEXT NOT NULL,
            phase TEXT NOT NULL DEFAULT 'PLANIFIER',
            draft TEXT NOT NULL DEFAULT '',
            review TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX idx_memories_project ON memories(project, category)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun remember(category: String, content: String, project: String = "", verified: Boolean = false): Long {
        require(category in setOf("preference", "decision", "error", "correction", "summary", "lesson"))
        require(content.isNotBlank() && content.length <= 12000)
        val values = ContentValues().apply {
            put("category", category)
            put("project", project.take(160))
            put("content", content)
            put("verified", if (verified) 1 else 0)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("memories", null, values)
    }

    data class Memory(val id: Long, val category: String, val project: String,
                      val content: String, val verified: Boolean)

    fun recall(project: String = "", limit: Int = 20): List<Memory> {
        val result = mutableListOf<Memory>()
        val selection = if (project.isBlank()) null else "(project = ? OR project = '')"
        val args = if (project.isBlank()) null else arrayOf(project)
        readableDatabase.query("memories", arrayOf("id", "category", "project", "content", "verified"),
            selection, args, null, null, "created_at DESC", limit.coerceIn(1, 100).toString()).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Memory(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getInt(4) == 1))
            }
        }
        return result
    }

    fun deleteMemory(id: Long): Boolean =
        writableDatabase.delete("memories", "id = ?", arrayOf(id.toString())) > 0

    fun createPlan(project: String, objective: String): Long {
        require(project.isNotBlank() && objective.isNotBlank())
        val values = ContentValues().apply {
            put("project", project.take(160))
            put("objective", objective.take(12000))
            put("phase", "PLANIFIER")
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("plans", null, values)
    }

    /** Ne jamais écrire les brouillons dans un projet sans confirmation explicite. */
    fun updatePlan(id: Long, phase: String, draft: String = "", review: String = ""): Boolean {
        require(phase in setOf("PLANIFIER", "EXECUTER", "VERIFIER", "APPROUVE", "REJETE"))
        val values = ContentValues().apply {
            put("phase", phase)
            put("draft", draft.take(30000))
            put("review", review.take(12000))
        }
        return writableDatabase.update("plans", values, "id = ?", arrayOf(id.toString())) > 0
    }
}
