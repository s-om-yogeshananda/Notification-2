package com.example.customshade

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * স্ক্রিনের উপরের বাম অর্ধেক  -> নোটিফিকেশন প্যানেল
 * স্ক্রিনের উপরের ডান অর্ধেক  -> কন্ট্রোল সেন্টার
 * স্ট্যাটাস বারের উপর অদৃশ্য স্ট্রিপ বসিয়ে সোয়াইপ ধরা হয়।
 */
class ShadeAccessibilityService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var leftStrip: View? = null
    private var rightStrip: View? = null
    private var host: PanelHost? = null

    val statusBarHeight: Int
        get() {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addStrips()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != packageName && pkg != "com.android.systemui") {
                host?.closeNow()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        host?.closeNow()
        addStrips()
    }

    override fun onDestroy() {
        host?.closeNow()
        removeStrips()
        super.onDestroy()
    }

    // ---------- স্ট্রিপ ----------

    private fun addStrips() {
        removeStrips()
        val widthPx = resources.displayMetrics.widthPixels
        val half = widthPx / 2
        val h = statusBarHeight
        leftStrip = makeStrip(PanelKind.NOTIFICATIONS, Gravity.TOP or Gravity.START, half, h)
        rightStrip = makeStrip(PanelKind.CONTROLS, Gravity.TOP or Gravity.END, widthPx - half, h)
    }

    private fun removeStrips() {
        listOf(leftStrip, rightStrip).forEach { v ->
            if (v != null) {
                try { wm.removeView(v) } catch (_: Exception) {}
            }
        }
        leftStrip = null
        rightStrip = null
    }

    private fun makeStrip(kind: PanelKind, gravity: Int, w: Int, h: Int): View {
        val v = View(this)
        attachGesture(v, kind)
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = gravity
        wm.addView(v, lp)
        return v
    }

    private fun attachGesture(v: View, kind: PanelKind) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startY = 0f
        var dragging = false
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = ev.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - startY
                    if (!dragging && dy > slop) {
                        dragging = openHost(kind) != null
                    }
                    if (dragging) host?.dragTo(dy)
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) host?.finishDrag(ev.rawY - startY > dp(80))
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) host?.finishDrag(false)
                    dragging = false
                }
            }
            true
        }
    }

    // ---------- প্যানেল ----------

    @Suppress("DEPRECATION")
    private fun openHost(kind: PanelKind): PanelHost? {
        host?.let { return if (it.kind == kind) it else null }
        val h = PanelHost(this, kind) { closed -> removeHost(closed) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 40
        }
        return try {
            wm.addView(h, lp)
            host = h
            h
        } catch (e: Exception) {
            null
        }
    }

    private fun removeHost(h: PanelHost) {
        try { wm.removeView(h) } catch (_: Exception) {}
        if (host === h) host = null
    }

    // ---------- প্যানেল থেকে ব্যবহারের হেল্পার ----------

    fun closePanel() {
        host?.close()
    }

    fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "এই সেটিংস খোলা গেল না", Toast.LENGTH_SHORT).show()
        }
        host?.close()
    }

    fun global(action: Int) {
        performGlobalAction(action)
    }
}
