package com.tvinspector.app.scanner

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.tvinspector.app.models.SectionResult
import com.tvinspector.app.utils.SectionBuilder
import com.tvinspector.app.utils.Util
import com.tvinspector.app.utils.Util.yesNo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hasPkg(pm: PackageManager, p: String) = try {
    pm.getPackageInfo(p, 0); true
} catch (e: Throwable) {
    false
}

object DeviceCollector {
    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        val pm = ctx.packageManager
        s.add("Manufacturer", Build.MANUFACTURER)
        s.add("Brand", Build.BRAND)
        s.add("Model", Build.MODEL)
        s.add("Device", Build.DEVICE)
        s.add("Product", Build.PRODUCT)
        s.add("Hardware", Build.HARDWARE)
        s.add("Board", Build.BOARD)
        s.add("Android Version", Build.VERSION.RELEASE)
        s.add("Android API Level", Build.VERSION.SDK_INT)
        s.add("Build ID", Build.ID)
        s.add("Build Display", Build.DISPLAY)
        s.add("Build Fingerprint", Build.FINGERPRINT)
        s.add("Build Type / Tags", "${Build.TYPE} / ${Build.TAGS}")
        s.tryAdd("Build Date") { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(Build.TIME)) }
        s.add("Security Patch Level", Build.VERSION.SECURITY_PATCH)
        s.add("Kernel Version", System.getProperty("os.version"))
        s.add("Kernel (/proc/version)", Util.readFile("/proc/version"))
        s.add("Bootloader", Build.BOOTLOADER)
        s.add("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", "))
        s.add("Supported 32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "لا يوجد" })
        s.add("Supported 64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "لا يوجد" })
        s.add("32-bit / 64-bit",
            "32-bit: ${yesNo(Build.SUPPORTED_32_BIT_ABIS.isNotEmpty())} • 64-bit: ${yesNo(Build.SUPPORTED_64_BIT_ABIS.isNotEmpty())}")
        s.add("ro.board.platform", Util.prop("ro.board.platform"))
        s.add("ro.build.characteristics", Util.prop("ro.build.characteristics"))

        val tvMode = try {
            (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
                Configuration.UI_MODE_TYPE_TELEVISION
        } catch (e: Throwable) {
            false
        }
        val leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val gtvLauncher = hasPkg(pm, "com.google.android.apps.tv.launcherx")
        val atvLauncher = hasPkg(pm, "com.google.android.tvlauncher")
        val gms = hasPkg(pm, "com.google.android.gms")
        val play = hasPkg(pm, "com.android.vending")
        s.add("Android TV (UI mode TELEVISION)", yesNo(tvMode))
        s.add("Leanback feature (android.software.leanback)", yesNo(leanback))
        s.add("Google Mobile Services / Play Store", "GMS: ${yesNo(gms)} • Play Store: ${yesNo(play)}")
        s.add("Google TV (Launcher X مثبّت)", yesNo(gtvLauncher))
        s.add("Android TV Launcher (com.google.android.tvlauncher)", yesNo(atvLauncher))
        s.add(
            "نظام معدل من الشركة؟ (تقدير)",
            when {
                !leanback && !tvMode -> "غير TV — لا ينطبق"
                gtvLauncher || atvLauncher -> "لا — واجهة Google TV/Android TV الرسمية موجودة"
                else -> "محتمل — لا توجد واجهة Google الرسمية؛ غالبًا واجهة الشركة المصنعة"
            }
        )
        return s.build("device", "معلومات الجهاز", "📺")
    }
}

object CpuCollector {
    private val partNames = mapOf(
        "0xd03" to "Cortex-A53", "0xd04" to "Cortex-A35", "0xd05" to "Cortex-A55",
        "0xd07" to "Cortex-A57", "0xd08" to "Cortex-A72", "0xd09" to "Cortex-A73",
        "0xd0a" to "Cortex-A75", "0xd0b" to "Cortex-A76", "0xc07" to "Cortex-A7",
        "0xc09" to "Cortex-A9", "0xc0f" to "Cortex-A15", "0xc0e" to "Cortex-A17"
    )

    fun collect(): SectionResult {
        val s = SectionBuilder()
        val n = Runtime.getRuntime().availableProcessors()
        s.add("عدد الأنوية (availableProcessors)", n)
        s.add("Possible cores", Util.readFile("/sys/devices/system/cpu/possible"))
        s.add("Online cores", Util.readFile("/sys/devices/system/cpu/online"))
        s.add("Architecture (os.arch)", System.getProperty("os.arch"))
        s.add("Primary ABI", Build.SUPPORTED_ABIS.firstOrNull())
        s.add("Current process is 64-bit", yesNo(android.os.Process.is64Bit()))
        s.add("32-bit support", yesNo(Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()))
        s.add("64-bit support", yesNo(Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()))
        if (Build.VERSION.SDK_INT >= 31) {
            s.add("SoC Manufacturer", Build.SOC_MANUFACTURER)
            s.add("SoC Model", Build.SOC_MODEL)
        }
        s.add("Governor (cpu0)", Util.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"))

        val info = Util.readFile("/proc/cpuinfo")
        var blocks: List<Map<String, String>> = emptyList()
        if (info == null) {
            s.na("/proc/cpuinfo", "الملف غير قابل للقراءة على هذا الجهاز")
        } else {
            blocks = info.split(Regex("\\n\\s*\\n")).map { b ->
                b.lines().mapNotNull { l ->
                    val i = l.indexOf(':')
                    if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
                }.toMap()
            }
            fun first(vararg keys: String): String? =
                blocks.firstNotNullOfOrNull { b -> keys.firstNotNullOfOrNull { k -> b[k]?.ifBlank { null } } }
            s.add("Processor / model name", first("model name", "Processor"))
            s.add("Hardware (SoC)", first("Hardware"))
            s.add("CPU architecture", first("CPU architecture"))
            val feats = first("Features", "flags")
            s.add("CPU Features", feats)
            if (feats != null) {
                val set = feats.split(" ").toSet()
                val key = listOf("neon", "asimd", "aes", "pmull", "sha1", "sha2", "crc32", "fp", "vfp", "vfpv3",
                    "vfpv4", "idiv", "idivt", "atomics", "evtstrm", "sse4_2", "avx")
                s.add("تعليمات المعالج المدعومة (مختارة)", key.filter { it in set }.joinToString(", ").ifEmpty { "لا شيء من القائمة المرجعية" })
            }
        }

        var anyFreq = false
        for (i in 0 until n) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            fun mhz(f: String) = Util.readFile(base + f)?.toLongOrNull()?.let { it / 1000 }
            val min = mhz("cpuinfo_min_freq")
            val max = mhz("cpuinfo_max_freq")
            val cur = mhz("scaling_cur_freq")
            if (min != null || max != null || cur != null) anyFreq = true
            val blk = blocks.firstOrNull { it["processor"] == i.toString() }
            val part = blk?.get("CPU part")?.lowercase()
            val name = part?.let { partNames[it] ?: "CPU part $it" }
            val parts = ArrayList<String>()
            if (name != null) parts += name
            parts += "Min: ${min?.let { "$it MHz" } ?: "؟"}"
            parts += "Max: ${max?.let { "$it MHz" } ?: "؟"}"
            parts += "Cur: ${cur?.let { "$it MHz" } ?: "؟/offline"}"
            if (min == null && max == null && cur == null && name == null) s.na("Core $i", "لا يمكن قراءة cpufreq أو cpuinfo")
            else s.add("Core $i", parts.joinToString(" • "))
        }
        if (!anyFreq) s.na("Frequencies", "مسارات cpufreq محجوبة (قيود SELinux/الشركة المصنعة)")
        return s.build("cpu", "المعالج CPU", "⚙️")
    }
}

object MemoryCollector {
    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val used = mi.totalMem - mi.availMem
        s.add("Total RAM", Util.bytes(mi.totalMem))
        s.add("Used RAM", Util.bytes(used))
        s.add("Available RAM", Util.bytes(mi.availMem))
        s.add("نسبة الاستهلاك", String.format(Locale.US, "%.1f%%", used * 100.0 / mi.totalMem))
        s.add("Low Memory state", yesNo(mi.lowMemory))
        s.add("Low Memory threshold", Util.bytes(mi.threshold))
        s.add("Low-RAM device (isLowRamDevice)", yesNo(am.isLowRamDevice))
        s.add("Per-app heap class", "${am.memoryClass} MB (large: ${am.largeMemoryClass} MB)")
        val mem = Util.readFile("/proc/meminfo")
        fun kb(key: String): Long? = mem?.lines()?.firstOrNull { it.startsWith("$key:") }
            ?.replace(Regex("[^0-9]"), "")?.toLongOrNull()?.times(1024)
        if (mem == null) s.na("/proc/meminfo", "الملف غير قابل للقراءة") else {
            s.add("Cached (/proc/meminfo)", kb("Cached")?.let { Util.bytes(it) })
            s.add("Swap Total", kb("SwapTotal")?.let { Util.bytes(it) })
        }
        return s.build("memory", "الذاكرة RAM", "🧠")
    }
}

object StorageCollector {
    private fun stat(path: String): String {
        val st = StatFs(path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        val used = total - free
        val pct = if (total > 0) used * 100.0 / total else 0.0
        return "Total ${Util.bytes(total)} • Used ${Util.bytes(used)} • Free ${Util.bytes(free)} (${String.format(Locale.US, "%.1f", pct)}%)"
    }

    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        s.tryAdd("Internal storage / Data (/data)") { stat(Environment.getDataDirectory().path) }
        s.tryAdd("System partition (/system)") { stat(Environment.getRootDirectory().path) }
        s.tryAdd("Primary external storage") {
            "${Environment.getExternalStorageState()} • " + stat(Environment.getExternalStorageDirectory().path)
        }
        try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val vols = sm.storageVolumes
            s.add("عدد وحدات التخزين (StorageManager)", vols.size)
            vols.forEachIndexed { i, v ->
                s.add(
                    "Volume ${i + 1}: ${v.getDescription(ctx)}",
                    "state=${v.state} • removable=${yesNo(v.isRemovable)} • primary=${yesNo(v.isPrimary)} • " +
                        "emulated=${yesNo(v.isEmulated)} • uuid=${v.uuid ?: "-"}"
                )
            }
        } catch (e: Throwable) {
            s.na("StorageManager volumes", Util.reason(e))
        }
        try {
            ctx.getExternalFilesDirs(null).filterNotNull().forEachIndexed { i, f ->
                val removable = try { Environment.isExternalStorageRemovable(f) } catch (e: Throwable) { false }
                val state = try { Environment.getExternalStorageState(f) } catch (e: Throwable) { "?" }
                val st = try { stat(f.path) } catch (e: Throwable) { "تعذّر قراءة المساحة" }
                s.add("Storage path ${i + 1} (${if (removable) "USB/SD قابل للإزالة" else "داخلي"})", "${f.path} • $state • $st")
            }
        } catch (e: Throwable) {
            s.na("External files dirs", Util.reason(e))
        }
        return s.build("storage", "التخزين Storage", "💾")
    }
}
