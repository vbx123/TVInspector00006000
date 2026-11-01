package com.tvinspector.app.scanner

import android.content.Context
import com.tvinspector.app.models.AppInfo
import com.tvinspector.app.models.InfoItem
import com.tvinspector.app.models.ScanResult
import com.tvinspector.app.models.SectionResult
import com.tvinspector.app.utils.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DeviceScanner(private val context: Context) {

    private class Step(val id: String, val title: String, val icon: String, val block: () -> SectionResult)

    /** Runs every check off the main thread. A failed section never aborts the whole scan. */
    suspend fun run(onProgress: (done: Int, total: Int, name: String, tests: Int) -> Unit): ScanResult =
        withContext(Dispatchers.Default) {
            val c = context.applicationContext
            var apps: List<AppInfo> = emptyList()
            val steps = listOf(
                Step("device", "معلومات الجهاز", "📺") { DeviceCollector.collect(c) },
                Step("cpu", "المعالج CPU", "⚙️") { CpuCollector.collect() },
                Step("memory", "الذاكرة RAM", "🧠") { MemoryCollector.collect(c) },
                Step("storage", "التخزين Storage", "💾") { StorageCollector.collect(c) },
                Step("display", "الشاشة Display", "🖥️") { DisplayCollector.collect(c) },
                Step("gpu", "الرسوميات GPU", "🎮") { GpuCollector.collect(c) },
                Step("network", "الشبكات Network", "🌐") { NetworkCollector.collect(c) },
                Step("hardware", "الأجهزة والمنافذ Hardware", "🔌") { HardwareCollector.collect(c) },
                Step("sensors", "المستشعرات Sensors", "📡") { HardwareCollector.sensors(c) },
                Step("applications", "التطبيقات المثبتة", "📦") {
                    apps = AppsCollector.collectApps(c)
                    AppsCollector.summary(c, apps)
                },
                Step("launcher", "التوافق والـ Launcher", "🏠") { AppsCollector.launcher(c, apps) }
            )
            val out = ArrayList<SectionResult>()
            var tests = 0
            steps.forEachIndexed { i, st ->
                val r = try {
                    st.block()
                } catch (e: Throwable) {
                    SectionResult(st.id, st.title, st.icon,
                        listOf(InfoItem("خطأ في القسم", "تعذّر إكمال القسم: ${Util.reason(e)}", false)))
                }
                out += r
                tests += r.items.size
                withContext(Dispatchers.Main) { onProgress(i + 1, steps.size, st.title, tests) }
                delay(80)
            }
            ScanResult(out, apps, System.currentTimeMillis(), tests)
        }
}
