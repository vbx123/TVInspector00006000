package com.tvinspector.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.tvinspector.app.databinding.ActivityMainBinding
import com.tvinspector.app.models.ScanResult
import com.tvinspector.app.models.ScanStore
import com.tvinspector.app.scanner.DeviceScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvSummary.text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) • ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}"
        b.dashboard.layoutManager = GridLayoutManager(this, 4)
        b.btnScan.setOnClickListener { startScan() }
        b.btnScan.requestFocus()
        ScanStore.result?.let { showDashboard(it) }
    }

    private fun startScan() {
        if (job?.isActive == true) return
        b.dashboard.visibility = View.GONE
        b.tvStatus.text = "الحالة: جارٍ الفحص…"
        b.progress.progress = 0
        b.btnScan.text = "جارٍ الفحص…"
        job = lifecycleScope.launch {
            try {
                val r = DeviceScanner(applicationContext).run { done, total, name, tests ->
                    val pct = done * 100 / total
                    b.progress.progress = pct
                    b.tvPercent.text = "التقدم: $pct%"
                    b.tvTests.text = "الاختبارات المنفذة: $tests"
                    b.tvStatus.text = "الحالة: تم فحص $name ($done/$total)"
                }
                ScanStore.result = r
                b.tvStatus.text = "الحالة: اكتمل الفحص — اضغط ↓ لعرض النتائج"
                showDashboard(r)
            } catch (e: Exception) {
                b.tvStatus.text = "الحالة: توقف الفحص (${e.javaClass.simpleName})"
            } finally {
                b.btnScan.text = "إعادة الفحص"
            }
        }
    }

    private fun showDashboard(r: ScanResult) {
        b.tvTests.text = "الاختبارات المنفذة: ${r.testsRun}"
        b.progress.progress = 100
        b.tvPercent.text = "التقدم: 100%"
        b.btnScan.text = "إعادة الفحص"
        val cards = r.sections.map {
            CardData(it.id, it.icon, it.title,
                if (it.id == "applications") "${r.apps.size} تطبيق" else "${it.items.size} عنصر")
        } + CardData("reports", "📄", "التقارير", "TXT • JSON • نسخ • مشاركة")
        b.dashboard.adapter = CardAdapter(cards) { c ->
            when (c.id) {
                "applications" -> startActivity(Intent(this, AppsActivity::class.java))
                "reports" -> startActivity(Intent(this, ReportsActivity::class.java))
                else -> startActivity(Intent(this, DetailActivity::class.java).putExtra("id", c.id))
            }
        }
        b.dashboard.visibility = View.VISIBLE
    }
}
