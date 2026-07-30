package com.example.networkscanner.db

import android.content.Context
import com.example.networkscanner.model.ScanResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class HistoryEntry(
    val id: Long = System.currentTimeMillis(),
    val target: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String = "",
    val resultJson: String = ""
)

object ScanHistoryStore {

    private const val FILE_NAME = "scan_history.json"
    private val gson = Gson()

    fun load(context: Context): MutableList<HistoryEntry> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return mutableListOf()
            val json = file.readText()
            val type = object : TypeToken<MutableList<HistoryEntry>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
    }

    fun save(context: Context, entries: List<HistoryEntry>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(gson.toJson(entries))
        } catch (_: Exception) {}
    }

    fun addEntry(context: Context, result: ScanResult, summary: String) {
        val entries = load(context).toMutableList()
        entries.add(0, HistoryEntry(
            target = result.target,
            type = result.type.name,
            timestamp = result.timestamp,
            summary = summary,
            resultJson = gson.toJson(result)
        ))
        // Keep only last 100 scans
        if (entries.size > 100) {
            entries.dropLast(entries.size - 100)
        }
        save(context, entries)
    }

    fun getEntry(context: Context, id: Long): ScanResult? {
        val entries = load(context)
        val entry = entries.firstOrNull { it.id == id } ?: return null
        return try {
            gson.fromJson(entry.resultJson, ScanResult::class.java)
        } catch (_: Exception) { null }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
