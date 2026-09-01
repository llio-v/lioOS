package com.lioos.app.nfc

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** Persists saved NFC tags to a JSON file in app-private storage. */
class NfcStore(context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "saved_tags.json")

    fun load(): MutableList<SavedTag> {
        if (!file.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<SavedTag>>() {}.type
            gson.fromJson<MutableList<SavedTag>>(file.readText(), type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(tags: List<SavedTag>) {
        file.writeText(gson.toJson(tags))
    }

    fun add(tag: SavedTag): List<SavedTag> {
        val list = load()
        list.add(0, tag)
        saveAll(list)
        return list
    }

    fun remove(tag: SavedTag): List<SavedTag> {
        val list = load()
        list.removeAll { it.savedAt == tag.savedAt && it.uidHex == tag.uidHex }
        saveAll(list)
        return list
    }
}
