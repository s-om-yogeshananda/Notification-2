package com.example.customshade

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.util.Date

/** বাম পাশ থেকে টানলে আসা নোটিফিকেশন প্যানেল। */
class NotificationsView(private val svc: ShadeAccessibilityService) : LinearLayout(svc) {

    private val list = LinearLayout(svc).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        val r = svc.dp(32).toFloat()
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#F2303A5C"), Color.parseColor("#F2603C52"))
        ).apply { cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        setPadding(svc.dp(16), svc.statusBarHeight + svc.dp(10), svc.dp(16), svc.dp(6))

        // হেডার: সময়/তারিখ + Clear all
        val header = LinearLayout(svc).apply {
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
        header.addView(left, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(svc).apply {
            text = "Clear all"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(svc.dp(16), svc.dp(8), svc.dp(16), svc.dp(8))
            background = GradientDrawable().apply {
                cornerRadius = svc.dp(20).toFloat()
                setColor(Color.argb(60, 255, 255, 255))
            }
            setOnClickListener { NotificationService.instance?.cancelAllNotifications() }
        })
        addView(header)

        val gap = View(svc).apply { layoutParams = LayoutParams(1, svc.dp(12)) }
        addView(gap)

        val scroll = ScrollView(svc).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // নিচের হ্যান্ডেল (এখান থেকে উপরে টানলে বন্ধ হয়)
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
        addView(f, LayoutParams(LayoutParams.MATCH_PARENT, svc.dp(36)))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationService.onChange = { reload() }
        reload()
    }

    override fun onDetachedFromWindow() {
        NotificationService.onChange = null
        super.onDetachedFromWindow()
    }

    private fun reload() {
        list.removeAllViews()
        val listener = NotificationService.instance
        if (listener == null) {
            list.addView(message("Notification access বন্ধ আছে।\nচালু করতে এখানে ট্যাপ করুন") {
                svc.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            return
        }
        val all: List<StatusBarNotification> = try {
            listener.activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val items = all
            .filter {
                it.packageName != svc.packageName &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .sortedByDescending { it.postTime }
        if (items.isEmpty()) {
            list.addView(message("NO NOTIFICATIONS", null))
            return
        }
        items.forEach { list.addView(card(it)) }
    }

    private fun message(text: String, onClick: (() -> Unit)?): View = TextView(svc).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#AAFFFFFF"))
        setPadding(0, svc.dp(60), 0, svc.dp(60))
        if (onClick != null) setOnClickListener { onClick() }
    }

    private fun card(sbn: StatusBarNotification): View {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val app = try {
            svc.packageManager.getApplicationLabel(
                svc.packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        val time = DateFormat.getTimeFormat(svc).format(Date(sbn.postTime))

        val c = LinearLayout(svc).apply {
            orientation = VERTICAL
            setPadding(svc.dp(16), svc.dp(12), svc.dp(16), svc.dp(12))
            background = GradientDrawable().apply {
                cornerRadius = svc.dp(22).toFloat()
                setColor(Color.argb(50, 255, 255, 255))
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = svc.dp(8) }
            setOnClickListener {
                try { n.contentIntent?.send() } catch (_: PendingIntent.CanceledException) {}
                svc.closePanel()
            }
        }

        val top = LinearLayout(svc).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(svc).apply {
            this.text = "$app  •  $time"
            textSize = 12f
            setTextColor(Color.parseColor("#BBFFFFFF"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (sbn.isClearable) {
            top.addView(TextView(svc).apply {
                this.text = "✕"
                textSize = 14f
                setTextColor(Color.parseColor("#CCFFFFFF"))
                setPadding(svc.dp(12), svc.dp(4), svc.dp(4), svc.dp(4))
                setOnClickListener { NotificationService.instance?.cancelNotification(sbn.key) }
            })
        }
        c.addView(top)

        if (title.isNotEmpty()) {
            c.addView(TextView(svc).apply {
                this.text = title
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        if (text.isNotEmpty()) {
            c.addView(TextView(svc).apply {
                this.text = text
                textSize = 14f
                setTextColor(Color.parseColor("#DDFFFFFF"))
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        return c
    }
}
