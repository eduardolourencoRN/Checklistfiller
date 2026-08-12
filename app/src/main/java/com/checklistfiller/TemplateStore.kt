package com.checklistfiller

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FieldEntry(
    val matchType: String,
    val matchKey: String,
    val kind: String,
    var value: String,
    var variable: Boolean = false,
    val label: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("matchType", matchType)
        put("matchKey", matchKey)
        put("kind", kind)
        put("value", value)
        put("variable", variable)
        put("label", label)
    }
    companion object {
        fun fromJson(o: JSONObject) = FieldEntry(
            matchType = o.optString("matchType", "hint"),
            matchKey = o.optString("matchKey", ""),
            kind = o.optString("kind", "text"),
            value = o.optString("value", ""),
            variable = o.optBoolean("variable", false),
            label = o.optString("label", "")
        )
    }
}

data class Template(
    val name: String,
    val targetPackage: String,
    val fields: MutableList<FieldEntry>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("targetPackage", targetPackage)
        val arr = JSONArray()
        fields.forEach { arr.put(it.toJson()) }
        put("fields", arr)
    }
    companion object {
        fun fromJson(o: JSONObject): Template {
            val list = mutableListOf<FieldEntry>()
            val arr = o.optJSONArray("fields") ?: JSONArray()
            for (i in 0 until arr.length()) list.add(FieldEntry.fromJson(arr.getJSONObject(i)))
            return Template(
                name = o.optString("name", "Sem nome"),
                targetPackage = o.optString("targetPackage", ""),
                fields = list
            )
        }
    }
}

object TemplateStore {
    private const val FILE_NAME = "templates.json"

    fun load(context: Context): MutableList<Template> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            val out = mutableListOf<Template>()
            for (i in 0 until arr.length()) out.add(Template.fromJson(arr.getJSONObject(i)))
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, templates: List<Template>) {
        val arr = JSONArray()
        templates.forEach { arr.put(it.toJson()) }
        File(context.filesDir, FILE_NAME).writeText(arr.toString())
    }

    fun upsert(context: Context, template: Template) {
        val all = load(context)
        val idx = all.indexOfFirst { it.name == template.name }
        if (idx >= 0) all[idx] = template else all.add(template)
        save(context, all)
    }

    fun delete(context: Context, name: String) {
        val all = load(context).filter { it.name != name }
        save(context, all)
    }
}
