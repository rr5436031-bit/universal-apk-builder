package com.universalapk.builder.storage

import android.content.Context
import com.universalapk.builder.models.BuildRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/** Riwayat build persisten (JSON di penyimpanan internal aplikasi). */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val lock = Any()
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<BuildRecord>> = _records.asStateFlow()

    private fun load(): List<BuildRecord> = runCatching {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            List(a.length()) { BuildRecord.fromJson(a.getJSONObject(it)) }
        }
    }.getOrDefault(emptyList())

    fun get(id: String): BuildRecord? = _records.value.firstOrNull { it.id == id }
    fun add(r: BuildRecord) = mutate { listOf(r) + it }
    fun update(id: String, f: (BuildRecord) -> BuildRecord) = mutate { list -> list.map { if (it.id == id) f(it) else it } }

    private fun mutate(f: (List<BuildRecord>) -> List<BuildRecord>) {
        synchronized(lock) {
            val next = f(_records.value).take(100)
            _records.value = next
            runCatching {
                val tmp = File(file.parentFile, "history.json.tmp")
                tmp.writeText(JSONArray().also { a -> next.forEach { a.put(it.toJson()) } }.toString())
                tmp.renameTo(file)
            }
        }
    }
}
