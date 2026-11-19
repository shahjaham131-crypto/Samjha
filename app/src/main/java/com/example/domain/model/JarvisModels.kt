package com.example.domain.model

import android.graphics.drawable.Drawable

enum class AssistantLanguage(val label: String, val localeTag: String) {
    HINGLISH("Hinglish", "en-IN"),
    ENGLISH("English", "en-US"),
    HINDI("हिंदी", "hi-IN")
}

enum class MessagePlatform(val displayName: String) {
    WHATSAPP("WhatsApp"),
    SMS("SMS")
}

enum class DeviceSettingType(val displayName: String) {
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    DISPLAY("Display"),
    SOUND("Sound & Volume"),
    BATTERY("Battery"),
    LOCATION("Location"),
    NOTIFICATION_ACCESS("Notification Access"),
    GENERAL_SETTINGS("System Settings")
}

enum class VolumeAction(val displayName: String) {
    UP("Increase Volume"),
    DOWN("Decrease Volume"),
    MUTE("Mute Media"),
    UNMUTE("Unmute Media")
}

enum class MediaPickType {
    IMAGE,
    VIDEO,
    ANY_MEDIA
}

enum class StepStatus {
    PENDING,
    RUNNING,
    AWAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED
}

sealed interface JarvisAction {
    data class LaunchApp(val appQuery: String) : JarvisAction
    data class DialContact(val contactQuery: String) : JarvisAction
    data class DraftMessage(
        val platform: MessagePlatform,
        val recipientQuery: String,
        val messageBody: String
    ) : JarvisAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : JarvisAction
    data class SetTimer(val durationSeconds: Int, val label: String) : JarvisAction
    data class CreateReminder(val title: String, val minutesFromNow: Int) : JarvisAction
    data class SearchWeb(val query: String) : JarvisAction
    data class OpenMediaPicker(val mediaType: MediaPickType) : JarvisAction
    data object OpenDocumentPicker : JarvisAction
    data object ReadNotifications : JarvisAction
    data class ToggleFlashlight(val enable: Boolean?) : JarvisAction
    data class AdjustVolume(val action: VolumeAction) : JarvisAction
    data class OpenDeviceSetting(val settingType: DeviceSettingType) : JarvisAction
    data class RememberPreference(val key: String, val value: String) : JarvisAction
    data class RequestSensitiveDelete(val target: String) : JarvisAction
    data class RespondOnly(val text: String, val isSampleAi: Boolean = false) : JarvisAction
}

data class TaskStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: StepStatus,
    val action: JarvisAction
)

sealed interface PendingConfirmation {
    val id: String
    val title: String
    val description: String
    val stepId: String?

    data class CallConfirmation(
        override val id: String,
        override val title: String,
        override val description: String,
        val displayName: String,
        val phoneNumber: String,
        override val stepId: String? = null
    ) : PendingConfirmation

    data class MessageConfirmation(
        override val id: String,
        override val title: String,
        override val description: String,
        val platform: MessagePlatform,
        val recipientName: String,
        val recipientAddress: String,
        val messageBody: String,
        override val stepId: String? = null
    ) : PendingConfirmation

    data class PreferenceConsentConfirmation(
        override val id: String,
        override val title: String,
        override val description: String,
        val key: String,
        val value: String,
        override val stepId: String? = null
    ) : PendingConfirmation

    data class ClearMemoryConfirmation(
        override val id: String,
        override val title: String,
        override val description: String,
        override val stepId: String? = null
    ) : PendingConfirmation
}

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long,
    val steps: List<TaskStep> = emptyList(),
    val isSampleAiNotice: Boolean = false
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null
)

data class RedactedNotification(
    val id: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val body: String,
    val timestampMillis: Long
)

data class SelectedMediaItem(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class MemoryFact(
    val id: Long = 0,
    val factKey: String,
    val factValue: String,
    val createdAtMillis: Long
)

data class AssistantContext(
    val language: AssistantLanguage,
    val memoryConsentGranted: Boolean,
    val userName: String?,
    val rememberedFacts: List<MemoryFact>,
    val notificationCount: Int,
    val flashlightOn: Boolean
)

data class AiCommandPlan(
    val assistantReply: String,
    val actions: List<JarvisAction>,
    val isSampleAiResponse: Boolean
)
