package com.tvinspector.app.scanner

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.tvinspector.app.models.AppInfo
import com.tvinspector.app.models.SectionResult
import com.tvinspector.app.utils.SectionBuilder
import com.tvinspector.app.utils.Util
import com.tvinspector.app.utils.Util.yesNo

object AppsCollector {
    fun collectApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val launcherSet = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toHashSet()
        val leanSet = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toHashSet()

        val out = ArrayList<AppInfo>()
        for (pi in pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS)) {
            try {
                val ai = pi.applicationInfo ?: continue
                val pkg = pi.packageName
                val touchOptional = pi.reqFeatures?.any {
                    it.name == PackageManager.FEATURE_TOUCHSCREEN && (it.flags and FeatureInfo.FLAG_REQUIRED) == 0
                } == true
                val lean = pkg in leanSet
                val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                out += AppInfo(
                    label = pm.getApplicationLabel(ai).toString(),
                    packageName = pkg,
                    versionName = pi.versionName ?: "-",
                    versionCode = pi.longVersionCode,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    launchable = launchIntent != null,
                    hasLauncherActivity = pkg in launcherSet,
                    hasLeanbackActivity = lean,
                    supportsRemote = lean || touchOptional
                )
            } catch (e: Throwable) {
                // skip a single broken package, never fail the whole scan
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun summary(ctx: Context, apps: List<AppInfo>): SectionResult {
        val s = SectionBuilder()
        s.add("إجمالي التطبيقات الظاهرة للفحص", apps.size)
        s.add("تطبيقات النظام", apps.count { it.isSystem })
        s.add("تطبيقات المستخدم", apps.count { !it.isSystem })
        s.add("قابلة للتشغيل (Launch Intent)", apps.count { it.launchable })
        s.add("لديها Activity لـ LAUNCHER", apps.count { it.hasLauncherActivity })
        s.add("تدعم Android TV Leanback", apps.count { it.hasLeanbackActivity })
        s.add("تدعم الريموت (تقدير: Leanback أو اللمس غير مطلوب)", apps.count { it.supportsRemote })
        s.add(
            "Package Visibility",
            if (Build.VERSION.SDK_INT >= 30) "Android 11+: تظهر فقط التطبيقات المصرّح بها عبر <queries> (القابلة للتشغيل/Home)"
            else "Android 10 وما دون: جميع التطبيقات ظاهرة"
        )
        s.add("القائمة الكاملة", "افتح بطاقة «التطبيقات» للبحث والتشغيل، أو صدّر التقرير")
        return s.build("applications", "التطبيقات المثبتة", "📦")
    }

    fun launcher(ctx: Context, apps: List<AppInfo>): SectionResult {
        val s = SectionBuilder()
        val pm = ctx.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        s.tryAdd("Home الحالي (الافتراضي)") {
            val r = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = r?.activityInfo?.packageName
            when {
                pkg == null -> null
                pkg == "android" -> "لم يُحدَّد Home افتراضي — النظام يعرض شاشة الاختيار (ResolverActivity)"
                else -> "${r?.loadLabel(pm)} ($pkg)"
            }
        }
        try {
            val homes = pm.queryIntentActivities(home, 0)
            s.add("تطبيقات تدعم CATEGORY_HOME", homes.size)
            homes.forEach {
                val sys = (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                s.add("Home: ${it.loadLabel(pm)}", "${it.activityInfo.packageName} • ${if (sys) "نظام" else "تطبيق خارجي"}")
            }
            val ext = homes.count { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            s.add("Launcher خارجي (غير نظام) مثبّت", yesNo(ext > 0))
            s.add("هل توجد أكثر من Launcher؟", yesNo(homes.size > 1))
        } catch (e: Throwable) {
            s.na("Home activities", Util.reason(e))
        }
        s.add("تطبيقات تدعم CATEGORY_LEANBACK_LAUNCHER", apps.count { it.hasLeanbackActivity })
        s.add("تطبيقات تدعم CATEGORY_LAUNCHER", apps.count { it.hasLauncherActivity })
        s.tryAdd("شاشة اختيار/إعدادات Home (ACTION_HOME_SETTINGS)") {
            val ok = Intent(Settings.ACTION_HOME_SETTINGS).resolveActivity(pm) != null
            if (ok) "متاحة — يمكن فتحها من الزر أسفل الشاشة" else "غير متاحة على هذا النظام"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            s.tryAdd("RoleManager: دور HOME متاح") {
                yesNo((ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager).isRoleAvailable(RoleManager.ROLE_HOME))
            }
        } else {
            s.na("RoleManager (ROLE_HOME)", "يتطلب Android 10+ (الجهاز API ${Build.VERSION.SDK_INT})")
        }
        s.add("هل هذا التطبيق مؤهل كـ Home؟", "لا — أداة فحص ولا تعلن CATEGORY_HOME")
        s.add(
            "تغيير Launcher الافتراضي",
            "لا يمكن لأي تطبيق تغييره تلقائيًا؛ يتم بقرار المستخدم (إعدادات النظام أو شاشة الاختيار). " +
                "تطبيق Launcher مستقبلي يجب أن يعلن CATEGORY_HOME و CATEGORY_DEFAULT ثم يطلب الدور عبر RoleManager (Android 10+)"
        )
        return s.build("launcher", "التوافق والـ Launcher", "🏠")
    }
}
