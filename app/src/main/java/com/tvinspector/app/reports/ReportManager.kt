package com.tvinspector.app.reports

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.tvinspector.app.models.InfoItem
import com.tvinspector.app.models.ScanResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportManager {
    private fun stamp(t: Long, p: String) = SimpleDateFormat(p, Locale.US).format(Date(t))

    fun toText(r: ScanResult): String {
        val sb = StringBuilder()
        sb.append("=== TV DEVICE INSPECTOR REPORT ===\n")
        sb.append("Generated: ").append(stamp(r.timestamp, "yyyy-MM-dd HH:mm:ss")).append('\n')
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Tests executed: ${r.testsRun}\n")
        for (s in r.sections) {
            sb.append("\n[${s.id.uppercase()}] ${s.title}\n")
            for (i in s.items) {
                sb.append("- ${i.label}: ${i.value}\n")
            }
            if (s.id == "applications") {
                sb.append("\nInstalled apps (${r.apps.size}):\n")
                for (a in r.apps) {
                    sb.append("* ${a.label} | ${a.packageName} | v${a.versionName} (${a.versionCode}) | ")
                    sb.append("system=${a.isSystem} launchable=${a.launchable} launcher=${a.hasLauncherActivity} ")
                    sb.append("leanback=${a.hasLeanbackActivity} remote=${a.supportsRemote}\n")
                }
            }
        }
        return sb.toString()
    }

    private fun items(list: List<InfoItem>) = JSONArray().also { arr ->
        list.forEach {
            arr.put(JSONObject().put("name", it.label).put("value", it.value).put("available", it.available))
        }
    }

    fun toJson(r: ScanResult): String {
        val root = JSONObject()
        root.put("report", "TV DEVICE INSPECTOR")
        root.put("generated_at", stamp(r.timestamp, "yyyy-MM-dd'T'HH:mm:ss"))
        root.put("tests_executed", r.testsRun)
        for (s in r.sections) {
            val o = JSONObject().put("title", s.title).put("items", items(s.items))
            if (s.id == "applications") {
                val arr = JSONArray()
                r.apps.forEach { a ->
                    arr.put(
                        JSONObject().put("name", a.label).put("package", a.packageName)
                            .put("version_name", a.versionName).put("version_code", a.versionCode)
                            .put("is_system", a.isSystem).put("launchable", a.launchable)
                            .put("has_launcher_activity", a.hasLauncherActivity)
                            .put("leanback", a.hasLeanbackActivity).put("remote_support", a.supportsRemote)
                    )
                }
                o.put("count", r.apps.size).put("installed", arr)
            }
            root.put(s.id, o)
        }
        return root.toString(2)
    }

    private fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "reports")
        d.mkdirs()
        return d
    }

    fun save(ctx: Context, r: ScanResult, json: Boolean): File {
        val f = File(dir(ctx), "TV_Device_Inspector_${stamp(r.timestamp, "yyyyMMdd_HHmmss")}.${if (json) "json" else "txt"}")
        f.writeText(if (json) toJson(r) else toText(r), Charsets.UTF_8)
        return f
    }

    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
}
