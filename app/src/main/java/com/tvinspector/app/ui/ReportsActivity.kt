package com.tvinspector.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvinspector.app.databinding.ActivityReportsBinding
import com.tvinspector.app.models.ScanStore
import com.tvinspector.app.reports.ReportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportsActivity : AppCompatActivity() {
    private lateinit var b: ActivityReportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(b.root)
        val r = ScanStore.result
        b.tvInfo.text = if (r == null) "لا توجد نتائج بعد — نفّذ «ابدأ الفحص الشامل» أولًا."
        else "التقرير جاهز (${r.testsRun} اختبار). تُحفظ الملفات في مجلد بيانات التطبيق على الجهاز."
        b.btnTxt.setOnClickListener { export(false) }
        b.btnJson.setOnClickListener { export(true) }
        b.btnCopy.setOnClickListener { copy() }
        b.btnShare.setOnClickListener { share() }
        b.btnTxt.requestFocus()
    }

    private fun export(json: Boolean) {
        val r = ScanStore.result ?: return toast("نفّذ الفحص أولًا")
        lifecycleScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { ReportManager.save(applicationContext, r, json) }
                b.tvInfo.text = "تم الحفظ:\n${f.absolutePath}"
            } catch (e: Exception) {
                toast("فشل التصدير: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun copy() {
        val r = ScanStore.result ?: return toast("نفّذ الفحص أولًا")
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TV Device Inspector", ReportManager.toText(r)))
            toast("تم نسخ التقرير إلى الحافظة")
        } catch (e: Exception) {
            toast("تعذّر النسخ (التقرير كبير جدًا؟) — استخدم التصدير أو المشاركة")
        }
    }

    private fun share() {
        val r = ScanStore.result ?: return toast("نفّذ الفحص أولًا")
        lifecycleScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { ReportManager.save(applicationContext, r, false) }
                val uri = ReportManager.uri(this@ReportsActivity, f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "TV Device Inspector report")
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "مشاركة التقرير"))
            } catch (e: Exception) {
                toast("لا يوجد تطبيق مشاركة متاح على هذا الجهاز")
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
