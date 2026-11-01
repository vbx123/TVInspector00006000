package com.tvinspector.app.scanner

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.DisplayMetrics
import android.view.Display
import com.tvinspector.app.models.SectionResult
import com.tvinspector.app.utils.SectionBuilder
import com.tvinspector.app.utils.Util.yesNo
import java.util.Locale

object DisplayCollector {
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    @Suppress("DEPRECATION")
    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        if (d == null) {
            s.na("Display", "لا توجد شاشة افتراضية")
            return s.build("display", "الشاشة Display", "🖥️")
        }
        val m = DisplayMetrics()
        d.getRealMetrics(m)
        val w = m.widthPixels
        val h = m.heightPixels
        s.add("Resolution (واجهة النظام)", "$w × $h px")
        s.add("Width / Height", "$w / $h px")
        s.tryAdd("Physical mode") { d.mode.let { "${it.physicalWidth} × ${it.physicalHeight} @ ${String.format(Locale.US, "%.2f", it.refreshRate)} Hz" } }
        s.add("Density (scale)", String.format(Locale.US, "%.2f", m.density))
        s.add("Density DPI", m.densityDpi)
        s.add("xdpi / ydpi", String.format(Locale.US, "%.1f / %.1f", m.xdpi, m.ydpi))
        s.add("Smallest width", "${ctx.resources.configuration.smallestScreenWidthDp} dp")
        s.add("Refresh rate", String.format(Locale.US, "%.2f Hz", d.refreshRate))
        val g = gcd(w, h)
        if (g > 0 && h > 0) s.add("Aspect ratio", "${w / g}:${h / g} (${String.format(Locale.US, "%.2f", w.toDouble() / h)})")
        s.tryAdd("Supported modes") {
            d.supportedModes.joinToString(" | ") {
                "${it.physicalWidth}×${it.physicalHeight}@${String.format(Locale.US, "%.0f", it.refreshRate)}"
            }
        }
        s.tryAdd("HDR") { yesNo(d.isHdr) }
        try {
            val caps = d.hdrCapabilities
            val names = caps?.supportedHdrTypes?.map {
                when (it) {
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                    else -> "HDR type $it"
                }
            }
            s.add("HDR types", if (names.isNullOrEmpty()) "غير مدعوم/غير معلن" else names.joinToString(", "))
        } catch (e: Throwable) {
            s.na("HDR types", e.javaClass.simpleName)
        }
        s.tryAdd("Wide color gamut") { yesNo(d.isWideColorGamut) }
        s.add("Display name", d.name)
        return s.build("display", "الشاشة Display", "🖥️")
    }
}

object GpuCollector {
    private class GlInfo(val vendor: String?, val renderer: String?, val version: String?, val extensions: String?)

    private fun queryGl(): GlInfo? {
        var dpy: EGLDisplay? = null
        var ctx: EGLContext? = null
        var surf: EGLSurface? = null
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (dpy == null || dpy == EGL14.EGL_NO_DISPLAY) return null
            val v = IntArray(2)
            if (!EGL14.eglInitialize(dpy, v, 0, v, 1)) return null
            val attr = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(dpy, attr, 0, configs, 0, 1, num, 0) || num[0] == 0) return null
            surf = EGL14.eglCreatePbufferSurface(
                dpy, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            ctx = EGL14.eglCreateContext(
                dpy, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return null
            return GlInfo(
                GLES20.glGetString(GLES20.GL_VENDOR), GLES20.glGetString(GLES20.GL_RENDERER),
                GLES20.glGetString(GLES20.GL_VERSION), GLES20.glGetString(GLES20.GL_EXTENSIONS)
            )
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                if (dpy != null && dpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surf != null) EGL14.eglDestroySurface(dpy, surf)
                    if (ctx != null) EGL14.eglDestroyContext(dpy, ctx)
                }
            } catch (e: Throwable) {
            }
        }
    }

    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        s.tryAdd("OpenGL ES (أعلى إصدار يدعمه الجهاز)") {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.glEsVersion
        }
        val gl = queryGl()
        if (gl == null) {
            s.na("GL_RENDERER", "تعذّر إنشاء EGL/GLES context")
            s.na("GL_VENDOR", "تعذّر إنشاء EGL/GLES context")
            s.na("GL_VERSION", "تعذّر إنشاء EGL/GLES context")
            s.na("GL_EXTENSIONS", "تعذّر إنشاء EGL/GLES context")
        } else {
            s.add("GL_RENDERER (GPU)", gl.renderer)
            s.add("GL_VENDOR", gl.vendor)
            s.add("GL_VERSION (ES 2.0 context)", gl.version)
            val ext = gl.extensions?.trim()?.split(" ")?.filter { it.isNotBlank() }
            s.add("GL_EXTENSIONS count", ext?.size)
            s.add("GL_EXTENSIONS", ext?.joinToString(", "))
        }
        try {
            val feats = ctx.packageManager.systemAvailableFeatures
            val vk = feats.firstOrNull { it.name == "android.hardware.vulkan.version" }
            val lvl = feats.firstOrNull { it.name == "android.hardware.vulkan.level" }
            if (vk == null) s.add("Vulkan", "غير مدعوم (الميزة غير معلنة)") else {
                val v = vk.version
                s.add("Vulkan", "مدعوم — ${v ushr 22}.${(v shr 12) and 0x3FF}.${v and 0xFFF}" + (lvl?.let { " • level ${it.version}" } ?: ""))
            }
        } catch (e: Throwable) {
            s.na("Vulkan", e.javaClass.simpleName)
        }
        return s.build("gpu", "الرسوميات GPU", "🎮")
    }
}
