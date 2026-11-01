package com.tvinspector.app.models

data class InfoItem(val label: String, val value: String, val available: Boolean = true)

data class SectionResult(val id: String, val title: String, val icon: String, val items: List<InfoItem>)

data class AppInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val launchable: Boolean,
    val hasLauncherActivity: Boolean,
    val hasLeanbackActivity: Boolean,
    val supportsRemote: Boolean
)

data class ScanResult(
    val sections: List<SectionResult>,
    val apps: List<AppInfo>,
    val timestamp: Long,
    val testsRun: Int
)

object ScanStore {
    @Volatile
    var result: ScanResult? = null
}
