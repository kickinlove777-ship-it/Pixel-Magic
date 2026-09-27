package com.pixelmagic.modules.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LoggedNotification(
    val key: String,
    val packageName: String,
    val appTitle: String,
    val content: String,
    val timestamp: Long,
    val isRemoved: Boolean = false
)

class NotificationLogService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val item = LoggedNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appTitle = if (title.isNotBlank()) "$appName: $title" else appName,
            content = text,
            timestamp = sbn.postTime
        )

        val currentList = _logList.value.toMutableList()
        currentList.add(0, item)
        if (currentList.size > 150) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logList.value = currentList
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        val currentList = _logList.value.map {
            if (it.key == sbn.key) it.copy(isRemoved = true) else it
        }
        _logList.value = currentList
    }

    companion object {
        private val _logList = MutableStateFlow<List<LoggedNotification>>(emptyList())
        val logList: StateFlow<List<LoggedNotification>> = _logList.asStateFlow()
    }
}
