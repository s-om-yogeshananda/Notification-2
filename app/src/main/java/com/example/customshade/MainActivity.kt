package com.example.customshade

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

/** সেটআপ স্ক্রিন: দরকারি পারমিশনগুলো এখান থেকে দিতে হবে। */
class MainActivity : Activity() {

    private lateinit var bAccess: Button
    private lateinit var bNotif: Button
    private lateinit var bWrite: Button
    private lateinit var bDnd: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "CustomShade"
            textSize = 26f
            setTextColor(Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "উপরের ধাপগুলো সেরে নিন। এরপর স্ক্রিনের উপরের বাম দিক থেকে নিচে টানলে নোটিফিকেশন, " +
                "ডান দিক থেকে টানলে কন্ট্রোল সেন্টার আসবে।"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(16))
        })

        bAccess = step(root) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        bNotif = step(root) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        bWrite = step(root) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        bDnd = step(root) { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }

        root.addView(TextView(this).apply {
            text = "টিপস (Realme/Oppo ColorOS): Settings → Battery → এই অ্যাপ → 'Allow background activity' ও " +
                "Auto-launch চালু করুন, আর Recent apps-এ অ্যাপটি লক করে রাখুন, নইলে সিস্টেম সার্ভিস বন্ধ করে দিতে পারে।"
            textSize = 13f
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val acc = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().contains("$packageName/")
        val notif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val write = Settings.System.canWrite(this)
        val dnd = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

        bAccess.text = mark(acc) + "১. Accessibility সার্ভিস চালু করুন (জরুরি)"
        bNotif.text = mark(notif) + "২. Notification access দিন (নোটিফিকেশন দেখাতে)"
        bWrite.text = mark(write) + "৩. Modify system settings (ব্রাইটনেস/অটো-রোটেট)"
        bDnd.text = mark(dnd) + "৪. Do Not Disturb access"
    }

    private fun mark(ok: Boolean) = if (ok) "✅  " else "⬜  "

    private fun step(root: LinearLayout, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        root.addView(
            b,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(8) }
        )
        return b
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
