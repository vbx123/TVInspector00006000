package com.tvinspector.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvinspector.app.databinding.ActivityAppsBinding
import com.tvinspector.app.models.AppInfo
import com.tvinspector.app.models.ScanStore

class AppsActivity : AppCompatActivity() {
    private lateinit var b: ActivityAppsBinding
    private lateinit var adapter: AppAdapter
    private var all: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(b.root)
        all = ScanStore.result?.apps ?: run { finish(); return }
        adapter = AppAdapter(packageManager, lifecycleScope) { launch(it) }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        filter("")
        b.list.post { b.list.requestFocus() }
    }

    private fun filter(q: String) {
        val t = q.trim().lowercase()
        val list = if (t.isEmpty()) all else all.filter {
            it.label.lowercase().contains(t) || it.packageName.lowercase().contains(t)
        }
        adapter.submit(list)
        b.tvCount.text = "${list.size} من ${all.size} تطبيق — اضغط OK على التطبيق لفتحه"
    }

    private fun launch(a: AppInfo) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(a.packageName)
            ?: packageManager.getLaunchIntentForPackage(a.packageName)
        if (i == null) {
            Toast.makeText(this, "لا توجد Activity قابلة للتشغيل لهذا التطبيق", Toast.LENGTH_LONG).show()
            return
        }
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر تشغيل التطبيق: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}
