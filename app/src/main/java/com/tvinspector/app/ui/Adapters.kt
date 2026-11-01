package com.tvinspector.app.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tvinspector.app.R
import com.tvinspector.app.databinding.ItemAppBinding
import com.tvinspector.app.databinding.ItemCardBinding
import com.tvinspector.app.databinding.ItemRowBinding
import com.tvinspector.app.models.AppInfo
import com.tvinspector.app.models.InfoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CardData(val id: String, val icon: String, val title: String, val summary: String)

class CardAdapter(private val items: List<CardData>, private val onClick: (CardData) -> Unit) :
    RecyclerView.Adapter<CardAdapter.VH>() {
    class VH(val b: ItemCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        h.b.tvIcon.text = c.icon
        h.b.tvTitle.text = c.title
        h.b.tvSummary.text = c.summary
        h.b.root.setOnClickListener { onClick(c) }
    }
}

class RowAdapter(private val items: List<InfoItem>) : RecyclerView.Adapter<RowAdapter.VH>() {
    class VH(val b: ItemRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val i = items[position]
        h.b.tvLabel.text = i.label
        h.b.tvValue.text = i.value
        h.b.tvValue.setTextColor(ContextCompat.getColor(h.b.root.context, if (i.available) R.color.text else R.color.warn))
    }
}

class AppAdapter(
    private val pm: PackageManager,
    private val scope: CoroutineScope,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {
    class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root) {
        var pkg: String? = null
    }

    private var data: List<AppInfo> = emptyList()
    private val cache = LruCache<String, Drawable>(60)

    fun submit(list: List<AppInfo>) {
        data = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = data.size

    private fun mark(v: Boolean) = if (v) "✓" else "✗"

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = data[position]
        h.pkg = a.packageName
        h.b.tvName.text = a.label
        h.b.tvPkg.text = a.packageName
        h.b.tvMeta.text = "v${a.versionName} (${a.versionCode}) • ${if (a.isSystem) "نظام" else "مستخدم"} • " +
            "تشغيل ${mark(a.launchable)} • Leanback ${mark(a.hasLeanbackActivity)} • ريموت ${mark(a.supportsRemote)}"
        val cached = cache.get(a.packageName)
        if (cached != null) {
            h.b.imgIcon.setImageDrawable(cached)
        } else {
            h.b.imgIcon.setImageDrawable(null)
            scope.launch {
                val d = withContext(Dispatchers.IO) {
                    try { pm.getApplicationIcon(a.packageName) } catch (e: Throwable) { null }
                }
                if (d != null) {
                    cache.put(a.packageName, d)
                    if (h.pkg == a.packageName) h.b.imgIcon.setImageDrawable(d)
                }
            }
        }
        h.b.root.setOnClickListener { onClick(a) }
    }
}
