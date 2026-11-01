package com.tvinspector.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvinspector.app.databinding.ActivityDetailBinding
import com.tvinspector.app.models.ScanStore

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        val id = intent.getStringExtra("id")
        val sec = ScanStore.result?.sections?.firstOrNull { it.id == id }
        if (sec == null) {
            finish()
            return
        }
        b.tvTitle.text = "${sec.icon} ${sec.title}"
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = RowAdapter(sec.items)
        b.list.post { b.list.requestFocus() }
        if (sec.id == "launcher") {
            b.btnAction.visibility = View.VISIBLE
            b.btnAction.text = "فتح إعدادات تطبيق الشاشة الرئيسية (Home)"
            b.btnAction.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "هذه الشاشة غير متاحة على هذا الجهاز", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
