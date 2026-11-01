package com.tvinspector.app.scanner

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.view.InputDevice
import com.tvinspector.app.models.SectionResult
import com.tvinspector.app.utils.SectionBuilder
import com.tvinspector.app.utils.Util
import com.tvinspector.app.utils.Util.yesNo
import java.net.NetworkInterface

object NetworkCollector {
    private fun ip(i: Int) = "${i and 0xff}.${(i shr 8) and 0xff}.${(i shr 16) and 0xff}.${(i shr 24) and 0xff}"

    @Suppress("DEPRECATION")
    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        val app = ctx.applicationContext
        val pm = app.packageManager
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            if (caps == null) {
                s.add("Internet connectivity", "لا توجد شبكة نشطة")
            } else {
                val t = ArrayList<String>()
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) t += "Wi-Fi"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) t += "Ethernet"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) t += "Cellular"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) t += "Bluetooth"
                s.add("Active network", t.joinToString(", ").ifEmpty { "Other" })
                s.add("Internet capability", yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)))
                s.add("Validated (الإنترنت يعمل فعليًا)", yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
                s.add("Downstream bandwidth (تقدير النظام)", "${caps.linkDownstreamBandwidthKbps} kbps")
                cm.getLinkProperties(net)?.let { lp ->
                    s.add("Interface (active)", lp.interfaceName)
                    s.add("DNS servers", lp.dnsServers.joinToString(", ") { it.hostAddress ?: "" })
                }
            }
            val eth = cm.allNetworks.any {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            }
            s.add("Ethernet (متصل)", yesNo(eth))
        } catch (e: Throwable) {
            s.na("Connectivity", Util.reason(e))
        }
        try {
            val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            s.add("Wi-Fi enabled", yesNo(wm.isWifiEnabled))
            val wi = wm.connectionInfo
            if (wi == null || wi.networkId == -1) {
                s.add("Wi-Fi connection", "غير متصل بشبكة Wi-Fi")
            } else {
                val ssid = wi.ssid
                if (ssid == null || ssid.contains("unknown ssid")) {
                    s.na("Wi-Fi SSID", "يتطلب صلاحية الموقع ACCESS_FINE_LOCATION (قيد Android 8.1+) ولم تُطلب لتقليل الصلاحيات")
                } else s.add("Wi-Fi SSID", ssid.trim('"'))
                s.add("Wi-Fi signal (RSSI)", "${wi.rssi} dBm")
                s.add("Wi-Fi link speed", "${wi.linkSpeed} Mbps")
                s.add("Wi-Fi frequency", "${wi.frequency} MHz")
                s.add("Wi-Fi IP", ip(wi.ipAddress))
            }
        } catch (e: Throwable) {
            s.na("Wi-Fi", Util.reason(e))
        }
        try {
            val ifs = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (ni in ifs) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses.toList().filter { !it.isLoopbackAddress }.mapNotNull { it.hostAddress }
                s.add("IP — ${ni.name}", addrs.joinToString(", ").ifEmpty { "بدون عنوان" })
            }
            var macShown = false
            for (ni in ifs) {
                if (!(ni.name.startsWith("wlan") || ni.name.startsWith("eth"))) continue
                macShown = true
                val mac = ni.hardwareAddress?.joinToString(":") { "%02X".format(it) }
                if (mac == null || mac == "02:00:00:00:00:00") s.na("MAC — ${ni.name}", "Android 6+ يخفي عنوان MAC عن التطبيقات العادية")
                else s.add("MAC — ${ni.name}", mac)
            }
            if (!macShown) s.na("MAC Address", "لا توجد واجهة wlan/eth ظاهرة")
        } catch (e: Throwable) {
            s.na("Network interfaces", Util.reason(e))
        }
        try {
            if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                s.add("Bluetooth", "غير مدعوم على هذا الجهاز")
            } else {
                val ad = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                if (ad == null) s.add("Bluetooth", "لا يوجد محوّل") else {
                    s.add("Bluetooth enabled", yesNo(ad.isEnabled))
                    s.add("Bluetooth LE", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)))
                    s.tryAdd("Bluetooth name") { ad.name }
                }
            }
        } catch (e: Throwable) {
            s.na("Bluetooth", Util.reason(e) + " (Android 12+ يتطلب BLUETOOTH_CONNECT)")
        }
        return s.build("network", "الشبكات Network", "🌐")
    }
}

object HardwareCollector {
    private fun has(src: Int, f: Int) = (src and f) == f

    private fun audioType(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line analog"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line digital"
        AudioDeviceInfo.TYPE_TV_TUNER -> "TV tuner"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote submix"
        else -> "Type $t"
    }

    fun collect(ctx: Context): SectionResult {
        val s = SectionBuilder()
        val pm = ctx.packageManager
        val feats = listOf(
            "Camera" to PackageManager.FEATURE_CAMERA_ANY,
            "Microphone" to PackageManager.FEATURE_MICROPHONE,
            "Audio output" to PackageManager.FEATURE_AUDIO_OUTPUT,
            "USB host" to PackageManager.FEATURE_USB_HOST,
            "USB accessory" to PackageManager.FEATURE_USB_ACCESSORY,
            "Bluetooth" to PackageManager.FEATURE_BLUETOOTH,
            "Wi-Fi" to PackageManager.FEATURE_WIFI,
            "Ethernet" to "android.hardware.ethernet",
            "HDMI CEC" to "android.hardware.hdmi.cec",
            "Touchscreen" to PackageManager.FEATURE_TOUCHSCREEN,
            "Gamepad" to "android.hardware.gamepad",
            "Live TV" to PackageManager.FEATURE_LIVE_TV
        )
        for ((name, f) in feats) s.add("Feature: $name", yesNo(pm.hasSystemFeature(f)))

        try {
            val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val list = um.deviceList.values.toList()
            s.add("USB devices connected", list.size)
            list.forEachIndexed { i, d ->
                val name = listOfNotNull(
                    try { d.manufacturerName } catch (e: Throwable) { null },
                    try { d.productName } catch (e: Throwable) { null }
                ).joinToString(" ").ifBlank { "unknown" }
                s.add("USB ${i + 1}", "$name • VID:${"%04X".format(d.vendorId)} PID:${"%04X".format(d.productId)} • class ${d.deviceClass} • ${d.deviceName}")
            }
        } catch (e: Throwable) {
            s.na("USB", Util.reason(e))
        }

        try {
            val ids = InputDevice.getDeviceIds()
            var count = 0
            for (id in ids) {
                val d = InputDevice.getDevice(id) ?: continue
                count++
                val types = ArrayList<String>()
                val src = d.sources
                if (has(src, InputDevice.SOURCE_DPAD)) types += "D-Pad/Remote"
                if (has(src, InputDevice.SOURCE_KEYBOARD)) {
                    types += if (d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) "Keyboard" else "Keys"
                }
                if (has(src, InputDevice.SOURCE_GAMEPAD)) types += "Gamepad"
                if (has(src, InputDevice.SOURCE_JOYSTICK)) types += "Joystick"
                if (has(src, InputDevice.SOURCE_MOUSE)) types += "Mouse"
                if (has(src, InputDevice.SOURCE_TOUCHSCREEN)) types += "Touchscreen"
                if (has(src, InputDevice.SOURCE_TOUCHPAD)) types += "Touchpad"
                s.add(
                    "Input #$id: ${d.name}",
                    "${types.joinToString(", ").ifEmpty { "other" }} • VID:${"%04X".format(d.vendorId)} PID:${"%04X".format(d.productId)}" +
                        if (d.isVirtual) " • virtual" else ""
                )
            }
            s.add("Input devices count", count)
        } catch (e: Throwable) {
            s.na("Input devices", Util.reason(e))
        }

        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList
            if (ids.isEmpty()) s.add("Cameras", "لا توجد كاميرات") else ids.forEach { id ->
                val facing = when (cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                    else -> "unknown"
                }
                s.add("Camera id $id", facing)
            }
        } catch (e: Throwable) {
            s.na("Cameras", Util.reason(e))
        }

        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val ins = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            s.add("Audio outputs (السماعات/المخارج)", outs.joinToString(" | ") { "${audioType(it.type)} ${it.productName}" }.ifEmpty { "لا يوجد" })
            s.add("Audio inputs (الميكروفون/المداخل)", ins.joinToString(" | ") { "${audioType(it.type)} ${it.productName}" }.ifEmpty { "لا يوجد" })
        } catch (e: Throwable) {
            s.na("Audio devices", Util.reason(e))
        }

        try {
            val ad = (ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (ad == null) s.add("Paired Bluetooth devices", "لا يوجد Bluetooth") else {
                val bonded = ad.bondedDevices
                s.add("Paired Bluetooth devices count", bonded.size)
                bonded.forEachIndexed { i, d -> s.add("Paired ${i + 1}", "${d.name ?: "?"} • class ${d.bluetoothClass?.majorDeviceClass}") }
            }
        } catch (e: Throwable) {
            s.na("Paired Bluetooth devices", Util.reason(e) + " (Android 12+ يتطلب BLUETOOTH_CONNECT)")
        }
        return s.build("hardware", "الأجهزة والمنافذ Hardware", "🔌")
    }

    fun sensors(ctx: Context): SectionResult {
        val s = SectionBuilder()
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val list = sm.getSensorList(Sensor.TYPE_ALL)
        s.add("Sensors count", list.size)
        if (list.isEmpty()) s.add("Sensors", "لا توجد مستشعرات (طبيعي في معظم أجهزة التلفزيون)")
        list.forEach {
            s.add(it.name, "${it.stringType} • vendor ${it.vendor} • range ${it.maximumRange} • res ${it.resolution} • ${it.power} mA")
        }
        return s.build("sensors", "المستشعرات Sensors", "📡")
    }
}
