package com.greenrobot.ide

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Persists the list of recently opened files as a small JSON array. */
class RecentStore(context: Context) {

    data class Entry(val uri: String, val name: String)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = obj.optString("uri")
                if (uri.isEmpty()) null else Entry(uri, obj.optString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(uri: String, name: String) {
        val updated = (listOf(Entry(uri, name)) + load().filterNot { it.uri == uri }).take(MAX)
        save(updated)
    }

    fun remove(uri: String) {
        save(load().filterNot { it.uri == uri })
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().put("uri", it.uri).put("name", it.name))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "items"
        private const val MAX = 20
    }
}
