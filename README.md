# TV DEVICE INSPECTOR

أداة فحص أجهزة Android TV — Kotlin / XML Views / minSdk 28 / targetSdk 34 — تعمل Offline بالكامل.

## البناء (الطريقة الأسهل: GitHub Actions)
1. ارفع محتويات هذا المجلد كما هي إلى مستودع GitHub (يجب أن يظهر `.github/workflows/build.yml`).
2. تبويب **Actions** ← **Build APK** ← انتظر النجاح.
3. نزّل الملف من **Artifacts**: `TV-Device-Inspector-APK` ← `app-debug.apk`.

## البناء من Android Studio (Hedgehog أو أحدث، JDK 17)
Open ← اختر المجلد ← Sync ← Build ← Build APK.
المسار الناتج: `app/build/outputs/apk/debug/app-debug.apk`

## البناء من سطر الأوامر
`gradle assembleDebug` (Gradle 8.7 + JDK 17 + Android SDK 34).

## التثبيت على التلفزيون
`adb connect <TV_IP>:5555` ثم `adb install app-debug.apk`
أو انسخه على USB وثبّته بمدير ملفات (مع السماح بالمصادر غير المعروفة).

## الصلاحيات
ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, BLUETOOTH (حتى API 30). لا موقع، لا إنترنت، لا Root.
معلومات تتطلب صلاحيات إضافية تظهر "غير متاح" مع السبب (اسم Wi-Fi، MAC، أسماء Bluetooth على Android 12+).

## مكان التقارير
`/Android/data/com.tvinspector.app/files/reports/` (TXT / JSON) + مشاركة عبر FileProvider.
