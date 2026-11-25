package com.example.customshade

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** ৩য় ছবির ডিজাইনের কন্ট্রোল সেন্টার। */
class ControlCenterView(private val svc: ShadeAccessibilityService) : LinearLayout(svc) {

    private class Tile(val view: TextView, val isOn: () -> Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val tiles = mutableListOf<Tile>()
    private val cr = svc.contentResolver
    private val audio = svc.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cam = svc.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val nm = svc.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var torchOn = false
    private var askedWrite = false
    private lateinit var brightSlider: VerticalSlider
    private lateinit var volSlider: VerticalSlider

    private val torchId: String? = try {
        cam.cameraIdList.firstOrNull { id ->
            val c = cam.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: Exception) {
        null
    }

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                refresh()
            }
        }
    }

    init {
        orientation = VERTICAL
        val r = svc.dp(32).toFloat()
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#F2303A5C"), Color.parseColor("#F2603C52"))
        ).apply { cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        setPadding(svc.dp(16), svc.statusBarHeight + svc.dp(10), svc.dp(16), svc.dp(6))

        addView(header())
        addView(space(12))
        addView(topRow())
        addView(space(14))
        addView(mainRow())
        addView(handleBar())
    }

    // ---------- UI অংশ ----------

    private fun space(h: Int) = View(svc).apply { layoutParams = LayoutParams(1, svc.dp(h)) }

    private fun card() = LinearLayout(svc).apply {
        background = GradientDrawable().apply {
            cornerRadius = svc.dp(28).toFloat()
            setColor(Color.argb(50, 255, 255, 255))
        }
    }

    private fun hRow() = LinearLayout(svc).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun header(): View {
        val row = LinearLayout(svc).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(svc).apply { orientation = VERTICAL }
        left.addView(TextClock(svc).apply {
            format12Hour = "h:mm a"
            format24Hour = "h:mm a"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        left.addView(TextClock(svc).apply {
            format12Hour = "EEE, d MMM"
            format24Hour = "EEE, d MMM"
            textSize = 14f
            setTextColor(Color.parseColor("#CCFFFFFF"))
        })
        row.addView(left, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(svc).apply {
            text = "⚙"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(svc.dp(12), svc.dp(8), svc.dp(8), svc.dp(8))
            setOnClickListener { svc.launch(Intent(Settings.ACTION_SETTINGS)) }
        })
        return row
    }

    private fun tile(
        glyph: String,
        desc: String,
        size: Int,
        isOn: () -> Boolean,
        onTap: () -> Unit
    ): TextView {
        val tv = TextView(svc).apply {
            text = glyph
            textSize = 20f
            gravity = Gravity.CENTER
            contentDescription = desc
            setOnClickListener {
                onTap()
                handler.postDelayed({ refresh() }, 350)
            }
        }
        val m = svc.dp(6)
        tv.layoutParams = LayoutParams(svc.dp(size), svc.dp(size)).apply { setMargins(m, m, m, m) }
        tiles += Tile(tv, isOn)
        return tv
    }

    private fun topRow(): View {
        val row = LinearLayout(svc).apply { orientation = HORIZONTAL }

        // কানেক্টিভিটি কার্ড (২x২)
        val connect = card().apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        val r1 = hRow()
        r1.addView(tile("✈", "Airplane mode", 52, { airplaneOn() }, { openAirplane() }))
        r1.addView(tile("⇅", "Mobile data", 52, { cellularOn() }, { openInternetPanel() }))
        val r2 = hRow()
        r2.addView(tile("📶", "Wi-Fi", 52, { wifiOn() }, { openWifiPanel() }))
        r2.addView(tile("ᛒ", "Bluetooth", 52, { bluetoothOn() },
            { svc.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }))
        connect.addView(r1)
        connect.addView(r2)

        // মিডিয়া কার্ড
        val mediaCard = card().apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        mediaCard.addView(TextView(svc).apply {
            text = "♫  Media"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CCFFFFFF"))
        })
        val ctrl = hRow()
        listOf(
            "⏮" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            "⏯" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            "⏭" to KeyEvent.KEYCODE_MEDIA_NEXT
        ).forEach { (g, k) ->
            ctrl.addView(TextView(svc).apply {
                text = g
                textSize = 26f
                setTextColor(Color.WHITE)
                setPadding(svc.dp(8), svc.dp(10), svc.dp(8), svc.dp(10))
                setOnClickListener { sendMedia(k) }
            })
        }
        mediaCard.addView(ctrl)

        row.addView(connect, LayoutParams(0, svc.dp(140), 1f).apply { rightMargin = svc.dp(7) })
        row.addView(mediaCard, LayoutParams(0, svc.dp(140), 1f).apply { leftMargin = svc.dp(7) })
        return row
    }

    private fun mainRow(): View {
        val row = LinearLayout(svc).apply { orientation = HORIZONTAL }

        val col1 = LinearLayout(svc).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val col2 = LinearLayout(svc).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val list = listOf(
            tile("🔦", "Flashlight", 60, { torchOn }, { toggleTorch() }),
            tile("🔄", "Auto-rotate", 60, { autoRotateOn() }, { toggleAutoRotate() }),
            tile("🌙", "Do Not Disturb", 60, { dndOn() }, { toggleDnd() }),
            tile("📍", "Location", 60, { locationOn() },
                { svc.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }),
            tile("📡", "Hotspot", 60, { false }, { openHotspot() }),
            tile("📸", "Screenshot", 60, { false }, {
                svc.closePanel()
                handler.postDelayed(
                    { svc.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 500
                )
            }),
            tile("🔒", "Lock screen", 60, { false }, {
                svc.closePanel()
                svc.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }),
            tile("⏻", "Power menu", 60, { false }, {
                svc.closePanel()
                svc.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            })
        )
        list.forEachIndexed { i, t -> (if (i % 2 == 0) col1 else col2).addView(t) }

        val sliderH = svc.dp(4 * 60 + 3 * 14)
        val sliderW = svc.dp(62)

        brightSlider = VerticalSlider(svc, "☀") { v -> setBrightness(v) }
        volSlider = VerticalSlider(svc, "♪") { v ->
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0)
            } catch (_: SecurityException) {}
        }
        val col3 = LinearLayout(svc).apply { gravity = Gravity.CENTER }
        col3.addView(brightSlider, LayoutParams(sliderW, sliderH))
        val col4 = LinearLayout(svc).apply { gravity = Gravity.CENTER }
        col4.addView(volSlider, LayoutParams(sliderW, sliderH))

        row.addView(col1, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(col2, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(col3, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(col4, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun handleBar(): View {
        val f = FrameLayout(svc)
        f.addView(
            View(svc).apply {
                background = GradientDrawable().apply {
                    cornerRadius = svc.dp(2).toFloat()
                    setColor(Color.argb(110, 255, 255, 255))
                }
            },
            FrameLayout.LayoutParams(svc.dp(40), svc.dp(4), Gravity.CENTER)
        )
        f.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, svc.dp(36))
        return f
    }

    // ---------- লাইফসাইকেল ----------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try { cam.registerTorchCallback(torchCb, handler) } catch (_: Exception) {}
        refresh()
        brightSlider.value = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        } catch (e: Exception) { 0.5f }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volSlider.value = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun onDetachedFromWindow() {
        try { cam.unregisterTorchCallback(torchCb) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        tiles.forEach { t ->
            val on = try { t.isOn() } catch (e: Exception) { false }
            t.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (on) Color.WHITE else Color.argb(64, 255, 255, 255))
            }
            t.view.setTextColor(if (on) Color.parseColor("#2B3045") else Color.WHITE)
        }
    }

    // ---------- অবস্থা পড়া ----------

    private fun airplaneOn() =
        Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun cellularOn(): Boolean {
        val cm = svc.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    private fun wifiOn(): Boolean {
        val wm = svc.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.isWifiEnabled
    }

    private fun bluetoothOn(): Boolean = try {
        svc.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (e: Exception) { false }

    private fun autoRotateOn() =
        Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun dndOn() =
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    private fun locationOn() =
        svc.getSystemService(LocationManager::class.java).isLocationEnabled

    // ---------- অ্যাকশন ----------

    private fun openAirplane() = svc.launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    private fun openWifiPanel() = svc.launch(
        Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
    )

    private fun openInternetPanel() = svc.launch(
        Intent(
            if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            else Settings.ACTION_WIRELESS_SETTINGS
        )
    )

    private fun openHotspot() {
        val i = Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
        if (i.resolveActivity(svc.packageManager) != null) svc.launch(i)
        else svc.launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    private fun toggleTorch() {
        val id = torchId ?: return
        try { cam.setTorchMode(id, !torchOn) } catch (_: Exception) {}
    }

    private fun askWrite() {
        svc.launch(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${svc.packageName}"))
        )
    }

    private fun toggleAutoRotate() {
        if (!Settings.System.canWrite(svc)) { askWrite(); return }
        val on = autoRotateOn()
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)
    }

    private fun toggleDnd() {
        if (!nm.isNotificationPolicyAccessGranted) {
            svc.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        nm.setInterruptionFilter(
            if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
    }

    private fun setBrightness(v: Float) {
        if (!Settings.System.canWrite(svc)) {
            if (!askedWrite) { askedWrite = true; askWrite() }
            return
        }
        Settings.System.putInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
    }

    private fun sendMedia(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
