package com.example.customshade

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: NotificationService? = null

        var onChange: (() -> Unit)? = null
    }

    override fun onListenerConnected() {
        instance = this
        onChange?.invoke()
    }

    override fun onListenerDisconnected() {
        instance = null
        onChange?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onChange?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onChange?.invoke()
    }
}
