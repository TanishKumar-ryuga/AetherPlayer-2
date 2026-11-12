package com.aether.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistStore {
    private fun file(c: Context) = File(c.filesDir, "playlists.json")
    fun load(c: Context): Map<String, List<Long>> {
        val f = file(c)
        if (!f.exists()) return emptyMap()
        return try {
            val o = JSONObject(f.readText())
            val m = LinkedHashMap<String, List<Long>>()
            o.keys().forEach { k -> val a = o.getJSONArray(k); m[k] = List(a.length()) { a.getLong(it) } }
            m
        } catch (e: Exception) { emptyMap() }
    }
    fun save(c: Context, m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        file(c).writeText(o.toString())
    }
}
