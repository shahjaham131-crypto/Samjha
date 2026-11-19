package com.example.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.domain.model.RedactedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object JarvisNotificationStore {
    private val _notifications = MutableStateFlow<List<RedactedNotification>>(emptyList())
    val notifications: StateFlow<List<RedactedNotification>> = _notifications.asStateFlow()

    fun updateFromActive(items: List<RedactedNotification>) {
        _notifications.value = items
            .sortedByDescending { it.timestampMillis }
            .take(25)
    }

    fun addOrUpdate(item: RedactedNotification) {
        val updated = (_notifications.value.filterNot { it.id == item.id } + item)
            .sortedByDescending { it.timestampMillis }
            .take(25)
        _notifications.value = updated
    }

    fun removeById(id: String) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val flat = ComponentName(context, JarvisNotificationService::class.java).flattenToString()
        return enabled.split(":").any { it.equals(flat, ignoreCase = true) }
    }

    /**
     * Redacts OTPs, PINs, passwords, and sensitive financial/auth codes
     * so Jarvis never exposes private credentials.
     */
    fun redactSensitiveText(raw: String): String {
        val lower = raw.lowercase()
        val sensitiveKeywords = listOf("otp", "code", "pin", "password", "passcode", "cvv", "secret", "verification")
        return if (sensitiveKeywords.any { lower.contains(it) }) {
            raw
                .replace(Regex("""\b\d{4,8}\b"""), "[REDACTED]")
                .replace(Regex("""(?i)(password|passcode|pin)\s*[:\-]?\s*\S+"""), "$1: [REDACTED]")
        } else {
            raw
        }
    }
}

class JarvisNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        val list = activeNotifications?.mapNotNull { toRedacted(it) }.orEmpty()
        JarvisNotificationStore.updateFromActive(list)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        toRedacted(sbn)?.let { JarvisNotificationStore.addOrUpdate(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        JarvisNotificationStore.removeById(sbn.key)
    }

    private fun toRedacted(sbn: StatusBarNotification): RedactedNotification? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null

        val appLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(sbn.packageName)

        return RedactedNotification(
            id = sbn.key,
            appName = appLabel,
            packageName = sbn.packageName,
            title = JarvisNotificationStore.redactSensitiveText(title.ifEmpty { appLabel }),
            body = JarvisNotificationStore.redactSensitiveText(text),
            timestampMillis = sbn.postTime
        )
    }
}
