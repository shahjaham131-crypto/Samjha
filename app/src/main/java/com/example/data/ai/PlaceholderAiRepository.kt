package com.example.data.ai

import com.example.domain.model.AiCommandPlan
import com.example.domain.model.AssistantContext
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.DeviceSettingType
import com.example.domain.model.JarvisAction
import com.example.domain.model.MediaPickType
import com.example.domain.model.MessagePlatform
import com.example.domain.model.VolumeAction

/**
 * Local deterministic command router + clearly labeled Sample AI placeholder.
 * Used because Firebase AI Logic setup was declined by the user.
 * No direct Gemini REST API calls or embedded API keys are used here.
 */
class PlaceholderAiRepository : AiRepository {
    override val isCloudAiConnected: Boolean = false

    override suspend fun processCommand(input: String, context: AssistantContext): AiCommandPlan {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return AiCommandPlan(
                assistantReply = "At your service. Please type or speak a command.",
                actions = emptyList(),
                isSampleAiResponse = false
            )
        }

        val isHindiOrHinglish = detectHindiOrHinglish(trimmed, context.language)
        val clauses = splitMultiStepClauses(trimmed)
        val actions = mutableListOf<JarvisAction>()

        for (clause in clauses) {
            val parsed = parseSingleClause(clause)
            if (parsed != null) {
                actions.add(parsed)
            }
        }

        if (actions.isNotEmpty()) {
            val summaryReply = buildActionIntroReply(actions, isHindiOrHinglish, context.userName)
            val hasSearchOnlySummary = actions.any { it is JarvisAction.SearchWeb }
            return AiCommandPlan(
                assistantReply = if (hasSearchOnlySummary) {
                    "$summaryReply\n\n[Sample AI Notice] Web search intent launched. Live AI article summarization requires Firebase AI Logic."
                } else {
                    summaryReply
                },
                actions = actions,
                isSampleAiResponse = hasSearchOnlySummary
            )
        }

        // Open-ended / conversational query fallback (clearly labeled Sample AI response)
        val greetingName = context.userName?.let { " $it" }.orEmpty()
        val sampleResponse = if (isHindiOrHinglish) {
            "[Sample AI Response — Firebase Required]\n" +
                "Namaste$greetingName! Main aapka command samajh gaya: \"$trimmed\".\n" +
                "Live Gemini AI reasoning aur detailed answers ke liye Firebase AI Logic enable karna zaroori hai. " +
                "Tab tak aap mujhse apps kholne (YouTube, WhatsApp, Instagram), alarms/timers lagane, calls/messages draft karne, flashlight/settings control karne, ya notifications padhne ke liye keh sakte hain."
        } else {
            "[Sample AI Response — Firebase Required]\n" +
                "Understood$greetingName. For open-ended reasoning, deep summarization, and generative answers to \"$trimmed\", Jarvis needs Firebase AI Logic enabled.\n" +
                "Meanwhile, all authorized Android tools—launching apps, alarms, timers, call/message drafts, device settings, files, and notification reading—are ready to execute."
        }

        return AiCommandPlan(
            assistantReply = sampleResponse,
            actions = listOf(JarvisAction.RespondOnly(sampleResponse, isSampleAi = true)),
            isSampleAiResponse = true
        )
    }

    private fun splitMultiStepClauses(input: String): List<String> {
        return input
            .split(Regex("""\s+(?:and then|then|and|aur|phir|fir|uske baad)\s+|[,;]+""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseSingleClause(clause: String): JarvisAction? {
        val lower = clause.lowercase()

        // 1. Sensitive deletion check (must ask confirmation)
        if (containsAny(lower, "delete", "clear history", "erase", "remove memory", "hatao", "mita do", "delete karo")) {
            val target = when {
                containsAny(lower, "memory", "preference", "yaad") -> "saved preference memory"
                containsAny(lower, "history", "chat", "messages") -> "conversation history"
                else -> "selected local records"
            }
            return JarvisAction.RequestSensitiveDelete(target)
        }

        // 2. Remember preference with consent
        val rememberRegex = Regex(
            """(?:remember(?: that)?|save preference|yaad rakhna(?: ki)?|yaad rakho(?: ki)?)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )
        rememberRegex.find(clause)?.let { match ->
            val rawFact = match.groupValues[1].trim()
            val (key, value) = extractPreferencePair(rawFact)
            return JarvisAction.RememberPreference(key = key, value = value)
        }

        // 3. Read notifications
        if (containsAny(lower, "notification", "notifications", "alerts", "padho", "नोटिफिकेशन")) {
            return JarvisAction.ReadNotifications
        }

        // 4. Flashlight control
        if (containsAny(lower, "flashlight", "torch", "flash light", "टॉर्च", "फ्लैशलाइट")) {
            val enable = when {
                containsAny(lower, "off", "band", "disable", "बंद") -> false
                containsAny(lower, "on", "chalu", "start", "enable", "चालू", "जलाओ") -> true
                else -> null
            }
            return JarvisAction.ToggleFlashlight(enable)
        }

        // 5. Volume control
        if (containsAny(lower, "volume", "sound", "awaaz", "awaz", "आवाज़")) {
            val volAction = when {
                containsAny(lower, "mute", "silent", "chup", "म्यूट") -> VolumeAction.MUTE
                containsAny(lower, "unmute", "अनम्यूट") -> VolumeAction.UNMUTE
                containsAny(lower, "down", "low", "decrease", "kam", "कम", "नीचे") -> VolumeAction.DOWN
                containsAny(lower, "up", "high", "increase", "badhao", "tez", "zyada", "बढ़ाओ", "तेज़") -> VolumeAction.UP
                else -> null
            }
            if (volAction != null) return JarvisAction.AdjustVolume(volAction)
        }

        // 6. Alarms
        if (containsAny(lower, "alarm", "अलार्म")) {
            val (hour, minute) = parseTimeOfDay(lower)
            return JarvisAction.SetAlarm(hour = hour, minute = minute, label = "Jarvis Alarm")
        }

        // 7. Timers
        if (containsAny(lower, "timer", "countdown", "टाइमर")) {
            val seconds = parseDurationSeconds(lower)
            return JarvisAction.SetTimer(durationSeconds = seconds, label = "Jarvis Timer")
        }

        // 8. Reminders / Calendar event
        if (containsAny(lower, "remind", "reminder", "याद दिलाना", "रिमाइंडर")) {
            val title = clause
                .replace(Regex("""(?:set a reminder(?: to| for)?|remind me(?: to)?|reminder lagao)""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { "Jarvis Reminder" }
            return JarvisAction.CreateReminder(title = title, minutesFromNow = 30)
        }

        // 9. Draft / Send Message (Always requires confirmation!)
        if (containsAny(lower, "message", "text ", "sms", "whatsapp", "bhejo", "मैसेज", "भेजो")) {
            val isOpenOnly = containsAny(lower, "open whatsapp", "launch whatsapp", "whatsapp kholo", "whatsapp chalao") &&
                !containsAny(lower, "ko", "to", "send", "bhejo", "message")
            if (isOpenOnly) {
                return JarvisAction.LaunchApp("WhatsApp")
            }
            val platform = if (lower.contains("sms")) MessagePlatform.SMS else MessagePlatform.WHATSAPP
            val (recipient, body) = parseMessageDetails(clause)
            return JarvisAction.DraftMessage(platform = platform, recipientQuery = recipient, messageBody = body)
        }

        // 10. Make Call (Always requires confirmation!)
        if (containsAny(lower, "call ", "dial ", "phone ", "कॉल", "फोन मिलाओ")) {
            val target = clause
                .replace(Regex("""(?:call|dial|phone|ko call karo|karo|करो|को कॉल)""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { "Contact" }
            return JarvisAction.DialContact(contactQuery = target)
        }

        // 11. Files, Photos & Documents (Zero-permission Photo Picker / SAF)
        if (containsAny(lower, "photo", "photos", "image", "gallery", "picture", "फोटो", "तस्वीर")) {
            return JarvisAction.OpenMediaPicker(MediaPickType.IMAGE)
        }
        if (containsAny(lower, "video", "videos", "वीडियो")) {
            return JarvisAction.OpenMediaPicker(MediaPickType.VIDEO)
        }
        if (containsAny(lower, "file", "files", "document", "documents", "pdf", "फाइल", "दस्तावेज़")) {
            return JarvisAction.OpenDocumentPicker
        }

        // 12. Device Settings
        if (containsAny(lower, "wifi", "wi-fi", "वाई-फाई")) {
            return JarvisAction.OpenDeviceSetting(DeviceSettingType.WIFI)
        }
        if (containsAny(lower, "bluetooth", "ब्लूटूथ")) {
            return JarvisAction.OpenDeviceSetting(DeviceSettingType.BLUETOOTH)
        }
        if (containsAny(lower, "brightness", "display", "screen", "डिस्प्ले")) {
            return JarvisAction.OpenDeviceSetting(DeviceSettingType.DISPLAY)
        }
        if (containsAny(lower, "battery", "बैटरी")) {
            return JarvisAction.OpenDeviceSetting(DeviceSettingType.BATTERY)
        }
        if (containsAny(lower, "location", "gps", "लोकेशन")) {
            return JarvisAction.OpenDeviceSetting(DeviceSettingType.LOCATION)
        }
        if (containsAny(lower, "settings", "setting", "सेटिंग्स", "सेटिंग")) {
            return JarvisAction.OpenDeviceSetting(DeviceSettingType.GENERAL_SETTINGS)
        }

        // 13. Web Search
        if (containsAny(lower, "search", "google", "lookup", "find online", "खोजो", "सर्च")) {
            val query = clause
                .replace(Regex("""(?:search(?: for| on google)?|google|find online|karo|खोजो|सर्च करो)""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { clause }
            return JarvisAction.SearchWeb(query)
        }

        // 14. Launch known or installed applications
        val knownApps = listOf(
            "youtube", "whatsapp", "instagram", "facebook", "chrome",
            "spotify", "maps", "google maps", "camera", "gmail",
            "calculator", "clock", "play store", "telegram", "snapchat",
            "यूट्यूब", "व्हाट्सएप", "इंस्टाग्राम", "फेसबुक", "क्रोम", "कैमरा"
        )
        val matchedApp = knownApps.firstOrNull { lower.contains(it) }
        if (matchedApp != null || containsAny(lower, "open ", "launch ", "start ", "kholo", "chalao", "खोलो", "चलाओ")) {
            val appName = matchedApp?.let { normalizeHindiAppName(it) } ?: clause
                .replace(Regex("""(?:open|launch|start|app|kholo|chalao|खोलो|चलाओ)""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (appName.isNotEmpty()) {
                return JarvisAction.LaunchApp(appName)
            }
        }

        return null
    }

    private fun normalizeHindiAppName(raw: String): String = when (raw.lowercase()) {
        "यूट्यूब", "youtube" -> "YouTube"
        "व्हाट्सएप", "whatsapp" -> "WhatsApp"
        "इंस्टाग्राम", "instagram" -> "Instagram"
        "फेसबुक", "facebook" -> "Facebook"
        "क्रोम", "chrome" -> "Chrome"
        "कैमरा", "camera" -> "Camera"
        "google maps" -> "Google Maps"
        "play store" -> "Play Store"
        else -> raw.replaceFirstChar { it.uppercase() }
    }

    private fun extractPreferencePair(rawFact: String): Pair<String, String> {
        val lower = rawFact.lowercase()
        return when {
            lower.contains("my name is") -> "User Name" to rawFact.substringAfter("is", rawFact).trim()
            lower.contains("mera naam") -> "User Name" to rawFact
                .replace(Regex("""mera naam|hai""", RegexOption.IGNORE_CASE), "")
                .trim()
            lower.contains("language") -> "Preferred Language" to rawFact
            else -> "Preference" to rawFact
        }
    }

    private fun parseTimeOfDay(text: String): Pair<Int, Int> {
        val regex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|baje|बजे)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 7
            val minute = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val period = match.groupValues[3].lowercase()
            if (period == "pm" && hour in 1..11) hour += 12
            if (period == "am" && hour == 12) hour = 0
            if (period == "baje" && text.contains("shaam", ignoreCase = true) && hour in 1..11) hour += 12
            return hour to minute
        }
        return 7 to 0
    }

    private fun parseDurationSeconds(text: String): Int {
        val match = Regex("""(\d+)\s*(second|sec|minute|min|hour|hr|मिनट|सेकंड)""", RegexOption.IGNORE_CASE).find(text)
        if (match != null) {
            val amount = match.groupValues[1].toIntOrNull() ?: 5
            val unit = match.groupValues[2].lowercase()
            return when {
                unit.startsWith("sec") || unit.contains("सेकंड") -> amount
                unit.startsWith("hour") || unit.startsWith("hr") -> amount * 3600
                else -> amount * 60
            }.coerceAtLeast(5)
        }
        return 300
    }

    private fun parseMessageDetails(clause: String): Pair<String, String> {
        // E.g. "WhatsApp Rahul saying Hello", "Send SMS to Mom saying Hi", or "Rahul ko message bhejo I am coming"
        val toMatch = Regex(
            """(?:to|ko|whatsapp|sms|message|text)\s+([A-Za-z0-9_+ .-]+?)\s+(?:saying|that|message|msg|bhejo|likho|:|-)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (toMatch != null) {
            val recipient = toMatch.groupValues[1]
                .replace(Regex("""\b(?:whatsapp|sms|message|par|pe|ko|to)\b""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { "Contact" }
            val body = toMatch.groupValues[2].trim().ifEmpty { "Hello!" }
            return recipient to body
        }
        val cleaned = clause
            .replace(Regex("""\b(?:send|draft|whatsapp|sms|message|bhejo|likho|par|ko|saying|that)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val parts = cleaned.split(" ", limit = 2)
        val recipient = parts.getOrNull(0)?.ifBlank { "Contact" } ?: "Contact"
        val body = parts.getOrNull(1)?.ifBlank { "Hello!" } ?: "Hello!"
        return recipient to body
    }

    private fun buildActionIntroReply(
        actions: List<JarvisAction>,
        isHindiOrHinglish: Boolean,
        userName: String?
    ): String {
        val sir = userName?.let { ", $it" }.orEmpty()
        val needsConfirm = actions.any {
            it is JarvisAction.DialContact ||
                it is JarvisAction.DraftMessage ||
                it is JarvisAction.RememberPreference ||
                it is JarvisAction.RequestSensitiveDelete
        }
        return if (isHindiOrHinglish) {
            if (needsConfirm) {
                "Ji$sir, maine ${actions.size} step plan ready kiya hai. Sensitive action ke liye aapki confirmation chahiye."
            } else {
                "Bilkul$sir, ${actions.size} step task execute kar raha hoon."
            }
        } else {
            if (needsConfirm) {
                "Prepared ${actions.size} step(s)$sir. Please confirm the sensitive action before I proceed."
            } else {
                "Executing ${actions.size} step(s)$sir."
            }
        }
    }

    private fun detectHindiOrHinglish(text: String, selected: AssistantLanguage): Boolean {
        if (selected == AssistantLanguage.HINDI || selected == AssistantLanguage.HINGLISH) {
            val hinglishKeywords = listOf("karo", "kholo", "lagao", "bhejo", "chalu", "band", "padho", "yaad", "mera", "meri", "baje", "aur", "phir")
            val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
            if (hasDevanagari || hinglishKeywords.any { text.lowercase().contains(it) }) return true
        }
        return selected == AssistantLanguage.HINDI
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }
}
