package com.tvinspector.app.utils

import com.tvinspector.app.models.InfoItem
import com.tvinspector.app.models.SectionResult
import java.io.File
import java.io.FileInputStream
import java.util.Locale

object Util {
    fun readFile(path: String): String? = try {
        val f = File(path)
        if (!f.exists()) null
        else FileInputStream(f).bufferedReader().use { it.readText() }.trim().ifEmpty { null }
    } catch (e: Throwable) {
        null
    }

    /** Reads a system property through the standard `getprop` tool (no hidden APIs, no root). */
    fun prop(key: String): String? = try {
        val p = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor()
        out.ifEmpty { null }
    } catch (e: Throwable) {
        null
    }

    fun bytes(b: Long): String {
        val gb = b / 1073741824.0
        return if (gb >= 1) String.format(Locale.US, "%.2f GB", gb)
        else String.format(Locale.US, "%.1f MB", b / 1048576.0)
    }

    fun yesNo(v: Boolean) = if (v) "نعم" else "لا"

    fun reason(e: Throwable) = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
}

class SectionBuilder {
    private val items = ArrayList<InfoItem>()

    fun add(label: String, value: Any?) {
        val v = value?.toString()
        items += if (v.isNullOrBlank()) InfoItem(label, "غير متاح", false) else InfoItem(label, v)
    }

    fun na(label: String, why: String) {
        items += InfoItem(label, "غير متاح — $why", false)
    }

    fun tryAdd(label: String, block: () -> Any?) {
        try {
            add(label, block())
        } catch (e: Throwable) {
            na(label, Util.reason(e))
        }
    }

    fun build(id: String, title: String, icon: String) = SectionResult(id, title, icon, items.toList())
}
