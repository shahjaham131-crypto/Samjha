package com.example

import com.example.data.ai.PlaceholderAiRepository
import com.example.domain.model.AssistantContext
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.DeviceSettingType
import com.example.domain.model.JarvisAction
import com.example.domain.model.MessagePlatform
import com.example.system.JarvisNotificationStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCommandUnitTest {

    private val repo = PlaceholderAiRepository()

    private fun baseContext(language: AssistantLanguage = AssistantLanguage.HINGLISH) = AssistantContext(
        language = language,
        memoryConsentGranted = false,
        userName = "Aarav",
        rememberedFacts = emptyList(),
        notificationCount = 0,
        flashlightOn = false
    )

    @Test
    fun parsesMultiStepEnglishAndHinglishCommands() = runTest {
        val plan = repo.processCommand(
            "Open YouTube and set an alarm for 7:30 am",
            baseContext(AssistantLanguage.ENGLISH)
        )
        assertFalse(plan.isSampleAiResponse)
        assertEquals(2, plan.actions.size)
        assertTrue(plan.actions[0] is JarvisAction.LaunchApp)
        assertEquals("YouTube", (plan.actions[0] as JarvisAction.LaunchApp).appQuery)
        val alarm = plan.actions[1] as JarvisAction.SetAlarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
    }

    @Test
    fun parsesHindiAndHinglishHardwareAndSettingsCommands() = runTest {
        val plan = repo.processCommand(
            "Flashlight chalu karo aur Wi-Fi settings kholo",
            baseContext(AssistantLanguage.HINGLISH)
        )
        assertEquals(2, plan.actions.size)
        assertEquals(JarvisAction.ToggleFlashlight(true), plan.actions[0])
        assertEquals(JarvisAction.OpenDeviceSetting(DeviceSettingType.WIFI), plan.actions[1])

        val hindiPlan = repo.processCommand("यूट्यूब खोलो", baseContext(AssistantLanguage.HINDI))
        assertEquals(1, hindiPlan.actions.size)
        assertEquals("YouTube", (hindiPlan.actions[0] as JarvisAction.LaunchApp).appQuery)
    }

    @Test
    fun parsesSensitiveActionsAndOpenEndedSampleAiNotice() = runTest {
        val msgPlan = repo.processCommand(
            "WhatsApp Rahul saying On my way",
            baseContext()
        )
        assertEquals(1, msgPlan.actions.size)
        val draft = msgPlan.actions[0] as JarvisAction.DraftMessage
        assertEquals(MessagePlatform.WHATSAPP, draft.platform)
        assertEquals("Rahul", draft.recipientQuery)
        assertEquals("On my way", draft.messageBody)

        val openEnded = repo.processCommand(
            "Explain quantum computing simply",
            baseContext(AssistantLanguage.ENGLISH)
        )
        assertTrue(openEnded.isSampleAiResponse)
        assertTrue(openEnded.assistantReply.contains("Sample AI Response"))
    }

    @Test
    fun redactsOtpsAndPasswordsInNotifications() {
        val otpText = "Your verification OTP is 482910. Do not share."
        val redacted = JarvisNotificationStore.redactSensitiveText(otpText)
        assertFalse(redacted.contains("482910"))
        assertTrue(redacted.contains("[REDACTED]"))

        val normalText = "Meeting starts at 4:00 PM in Room 12."
        assertEquals(normalText, JarvisNotificationStore.redactSensitiveText(normalText))
    }
}
