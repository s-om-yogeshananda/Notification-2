package com.example.customshade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** ছবির মতো উল্লম্ব স্লাইডার (ব্রাইটনেস / ভলিউম)। */
class VerticalSlider(
    context: Context,
    private val glyph: String,
    private val onChange: (Float) -> Unit
) : View(context) {

    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.dp(22).toFloat()
    }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w * 0.45f
        val rect = RectF(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, bg)
        c.save()
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(path)
        c.drawRect(0f, h * (1f - value), w, h, fill)
        c.restore()
        txt.color = if (value > 0.1f) Color.parseColor("#2B3045") else Color.WHITE
        c.drawText(glyph, w / 2f, h - context.dp(24), txt)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN ||
            e.actionMasked == MotionEvent.ACTION_MOVE ||
            e.actionMasked == MotionEvent.ACTION_UP
        ) {
            if (height > 0) {
                value = 1f - e.y / height
                onChange(value)
            }
        }
        return true
    }
}
