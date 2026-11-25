package com.example.customshade

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout

enum class PanelKind { NOTIFICATIONS, CONTROLS }

/** পুরো স্ক্রিন জুড়ে ওভারলে; ভিতরে উপর থেকে নেমে আসা প্যানেল। */
class PanelHost(
    private val service: ShadeAccessibilityService,
    val kind: PanelKind,
    private val onClosed: (PanelHost) -> Unit
) : FrameLayout(service) {

    private val content: View = when (kind) {
        PanelKind.CONTROLS -> ControlCenterView(service)
        PanelKind.NOTIFICATIONS -> NotificationsView(service)
    }
    private val slop = ViewConfiguration.get(service).scaledTouchSlop
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private var closing = false
    private var downY = 0f
    private var closeZone = false
    private var dragClosing = false

    init {
        val h = if (kind == PanelKind.NOTIFICATIONS)
            (resources.displayMetrics.heightPixels * 0.85f).toInt()
        else
            LayoutParams.WRAP_CONTENT
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, h, Gravity.TOP))
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> render() }
        render()
    }

    private fun render() {
        val h = content.height.toFloat()
        content.translationY = -(1f - progress) * (if (h == 0f) 3000f else h)
        setBackgroundColor(Color.argb((150 * progress).toInt(), 0, 0, 0))
    }

    fun dragTo(dy: Float) {
        if (closing) return
        anim?.cancel()
        val h = content.height
        progress = if (h > 0) (dy / h).coerceIn(0f, 1f) else 0f
        render()
    }

    fun finishDrag(open: Boolean) {
        if (closing) return
        if (open) animateTo(1f, false) else animateTo(0f, true)
    }

    fun close() {
        if (closing) return
        animateTo(0f, true)
    }

    fun closeNow() {
        anim?.cancel()
        closing = true
        onClosed(this)
    }

    private fun animateTo(target: Float, remove: Boolean) {
        anim?.cancel()
        if (remove) closing = true
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = 220
        a.addUpdateListener {
            progress = it.animatedValue as Float
            render()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (remove && !cancelled) onClosed(this@PanelHost)
            }
        })
        anim = a
        a.start()
    }

    // প্যানেলের নিচের অংশ বা বাইরে থেকে উপরে সোয়াইপ করলে বন্ধ হবে; বাইরে ট্যাপ করলেও।
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                dragClosing = false
                closeZone = ev.y > content.height - service.dp(56)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!closing && closeZone && downY - ev.y > slop) {
                    dragClosing = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (closing) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                dragClosing = false
                closeZone = ev.y > content.height - service.dp(56)
            }
            MotionEvent.ACTION_MOVE -> {
                if (closeZone && downY - ev.y > slop) dragClosing = true
                if (dragClosing && content.height > 0) {
                    anim?.cancel()
                    progress = (1f - (downY - ev.y) / content.height).coerceIn(0f, 1f)
                    render()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragClosing) {
                    if (progress < 0.75f) animateTo(0f, true) else animateTo(1f, false)
                } else if (ev.y > content.height) {
                    close()
                }
                dragClosing = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragClosing) animateTo(1f, false)
                dragClosing = false
            }
        }
        return true
    }
}
