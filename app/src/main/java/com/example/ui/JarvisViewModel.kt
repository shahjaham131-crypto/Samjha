package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.JarvisDatabase
import com.example.data.local.MemoryFactEntity
import com.example.data.local.MessageEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.AssistantContext
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.ChatMessage
import com.example.domain.model.DeviceSettingType
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.JarvisAction
import com.example.domain.model.MediaPickType
import com.example.domain.model.MemoryFact
import com.example.domain.model.MessagePlatform
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.RedactedNotification
import com.example.domain.model.SelectedMediaItem
import com.example.domain.model.StepStatus
import com.example.domain.model.TaskStep
import com.example.system.DeviceActionExecutor
import com.example.system.JarvisNotificationStore
import com.example.system.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface PickerLaunchEvent {
    data class LaunchMediaPicker(val mediaType: MediaPickType) : PickerLaunchEvent
    data object LaunchDocumentPicker : PickerLaunchEvent
}

data class PermissionSummary(
    val microphoneGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val notificationListenerEnabled: Boolean = false
)

data class JarvisUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputDraft: String = "",
    val isProcessing: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val voiceRms: Float = 0f,
    val partialTranscript: String = "",
    val language: AssistantLanguage = AssistantLanguage.HINGLISH,
    val ttsEnabled: Boolean = true,
    val memoryConsentGranted: Boolean = false,
    val rememberedFacts: List<MemoryFact> = emptyList(),
    val pendingConfirmations: List<PendingConfirmation> = emptyList(),
    val activeStepsByMessageId: Map<Long, List<TaskStep>> = emptyMap(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val recentNotifications: List<RedactedNotification> = emptyList(),
    val selectedFiles: List<SelectedMediaItem> = emptyList(),
    val flashlightOn: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val permissions: PermissionSummary = PermissionSummary(),
    val isCloudAiConnected: Boolean = false,
    val statusToast: String? = null
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val dao = JarvisDatabase.getInstance(appContext).jarvisDao()
    private val prefsRepo = UserPreferencesRepository(appContext)
    private val aiRepository: AiRepository = PlaceholderAiRepository()
    private val deviceExecutor = DeviceActionExecutor(appContext)
    private val voiceController = VoiceController(appContext)

    private val _inputDraft = MutableStateFlow("")
    private val _isProcessing = MutableStateFlow(false)
    private val _pendingConfirmations = MutableStateFlow<List<PendingConfirmation>>(emptyList())
    private val _activeStepsByMessageId = MutableStateFlow<Map<Long, List<TaskStep>>>(emptyMap())
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _selectedFiles = MutableStateFlow<List<SelectedMediaItem>>(emptyList())
    private val _permissions = MutableStateFlow(readPermissionSummary())
    private val _statusToast = MutableStateFlow<String?>(null)

    private val _pickerEvents = MutableSharedFlow<PickerLaunchEvent>(extraBufferCapacity = 4)
    val pickerEvents: SharedFlow<PickerLaunchEvent> = _pickerEvents.asSharedFlow()

    private val memoryFactsFlow = dao.observeMemoryFacts()

    val uiState: StateFlow<JarvisUiState> = combine(
        dao.observeMessages(),
        prefsRepo.preferencesFlow,
        memoryFactsFlow,
        _pendingConfirmations,
        _activeStepsByMessageId,
        _installedApps,
        JarvisNotificationStore.notifications,
        _selectedFiles,
        deviceExecutor.flashlightOn,
        deviceExecutor.mediaVolumePercent,
        _permissions,
        _inputDraft,
        _isProcessing,
        voiceController.isListening,
        voiceController.isSpeaking,
        voiceController.rmsDb,
        voiceController.partialTranscript,
        _statusToast
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val dbMessages = values[0] as List<MessageEntity>
        val prefs = values[1] as com.example.data.preferences.UserPreferencesState
        @Suppress("UNCHECKED_CAST")
        val dbFacts = values[2] as List<MemoryFactEntity>
        @Suppress("UNCHECKED_CAST")
        val confirmations = values[3] as List<PendingConfirmation>
        @Suppress("UNCHECKED_CAST")
        val stepMap = values[4] as Map<Long, List<TaskStep>>
        @Suppress("UNCHECKED_CAST")
        val apps = values[5] as List<InstalledAppInfo>
        @Suppress("UNCHECKED_CAST")
        val notifications = values[6] as List<RedactedNotification>
        @Suppress("UNCHECKED_CAST")
        val files = values[7] as List<SelectedMediaItem>
        val torchOn = values[8] as Boolean
        val volume = values[9] as Int
        val perms = values[10] as PermissionSummary
        val draft = values[11] as String
        val processing = values[12] as Boolean
        val listening = values[13] as Boolean
        val speaking = values[14] as Boolean
        val rms = values[15] as Float
        val partial = values[16] as String
        val toast = values[17] as String?

        val chatMessages = dbMessages.map { entity ->
            ChatMessage(
                id = entity.id,
                isUser = entity.isUser,
                text = entity.text,
                timestampMillis = entity.timestampMillis,
                steps = stepMap[entity.id].orEmpty(),
                isSampleAiNotice = entity.isSampleAiNotice
            )
        }

        JarvisUiState(
            messages = chatMessages,
            inputDraft = draft,
            isProcessing = processing,
            isListening = listening,
            isSpeaking = speaking,
            voiceRms = rms,
            partialTranscript = partial,
            language = prefs.language,
            ttsEnabled = prefs.ttsEnabled,
            memoryConsentGranted = prefs.memoryConsentGranted,
            rememberedFacts = dbFacts.map {
                MemoryFact(
                    id = it.id,
                    factKey = it.factKey,
                    factValue = it.factValue,
                    createdAtMillis = it.createdAtMillis
                )
            },
            pendingConfirmations = confirmations,
            activeStepsByMessageId = stepMap,
            installedApps = apps,
            recentNotifications = notifications,
            selectedFiles = files,
            flashlightOn = torchOn,
            mediaVolumePercent = volume,
            permissions = perms,
            isCloudAiConnected = aiRepository.isCloudAiConnected,
            statusToast = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = JarvisUiState()
    )

    init {
        seedWelcomeMessageIfEmpty()
        refreshInstalledApps()
        refreshPermissions()
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
        deviceExecutor.release()
    }

    private fun seedWelcomeMessageIfEmpty() {
        viewModelScope.launch {
            dao.observeMessages().collect { list ->
                if (list.isEmpty()) {
                    dao.insertMessage(
                        MessageEntity(
                            isUser = false,
                            text = "Good day. I am JARVIS, your personal Android assistant. Speak or type in English, Hindi, or Hinglish—for example: \"Open YouTube and set an alarm for 7 AM\" or \"Flashlight chalu karo\".",
                            timestampMillis = System.currentTimeMillis(),
                            isSampleAiNotice = false
                        )
                    )
                }
                return@collect
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                deviceExecutor.getInstalledLaunchableApps()
            }
            _installedApps.value = apps
        }
    }

    fun refreshPermissions() {
        _permissions.value = readPermissionSummary()
    }

    private fun readPermissionSummary(): PermissionSummary {
        val mic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val listener = JarvisNotificationStore.isNotificationAccessEnabled(appContext)
        return PermissionSummary(
            microphoneGranted = mic,
            contactsGranted = contacts,
            postNotificationsGranted = postNotif,
            notificationListenerEnabled = listener
        )
    }

    fun updateInputDraft(text: String) {
        _inputDraft.value = text
    }

    fun consumeStatusToast() {
        _statusToast.value = null
    }

    fun setLanguage(language: AssistantLanguage) {
        viewModelScope.launch {
            prefsRepo.setLanguage(language)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setTtsEnabled(enabled)
            if (!enabled) voiceController.stopSpeaking()
        }
    }

    fun setMemoryConsent(granted: Boolean) {
        viewModelScope.launch {
            prefsRepo.setMemoryConsent(granted)
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean = voiceController.isSpeechRecognitionAvailable()

    fun buildFallbackSpeechIntent(): android.content.Intent {
        return voiceController.buildSpeechRecognizerIntent(uiState.value.language)
    }

    fun startVoiceInput() {
        refreshPermissions()
        if (!_permissions.value.microphoneGranted) {
            _statusToast.value = "Microphone permission is required for voice commands."
            return
        }
        if (!voiceController.isSpeechRecognitionAvailable()) {
            _statusToast.value = "Speech recognition service unavailable on this device. Please type your command."
            return
        }
        voiceController.startListening(
            language = uiState.value.language,
            onFinalResult = { recognized ->
                submitCommand(recognized)
            },
            onError = { errorMsg ->
                _statusToast.value = errorMsg
            }
        )
    }

    fun stopVoiceInput() {
        voiceController.stopListening()
    }

    fun stopSpeaking() {
        voiceController.stopSpeaking()
    }

    fun submitCommand(rawCommand: String = _inputDraft.value) {
        val command = rawCommand.trim()
        if (command.isEmpty() || _isProcessing.value) return
        _inputDraft.value = ""

        viewModelScope.launch {
            _isProcessing.value = true
            val now = System.currentTimeMillis()
            dao.insertMessage(
                MessageEntity(
                    isUser = true,
                    text = command,
                    timestampMillis = now
                )
            )

            val current = uiState.value
            val userName = current.rememberedFacts
                .firstOrNull { it.factKey.equals("User Name", ignoreCase = true) }
                ?.factValue

            val context = AssistantContext(
                language = current.language,
                memoryConsentGranted = current.memoryConsentGranted,
                userName = userName,
                rememberedFacts = current.rememberedFacts,
                notificationCount = current.recentNotifications.size,
                flashlightOn = current.flashlightOn
            )

            val plan = aiRepository.processCommand(command, context)

            val assistantMsgId = dao.insertMessage(
                MessageEntity(
                    isUser = false,
                    text = plan.assistantReply,
                    timestampMillis = System.currentTimeMillis(),
                    isSampleAiNotice = plan.isSampleAiResponse
                )
            )

            val initialSteps = plan.actions
                .filterNot { it is JarvisAction.RespondOnly }
                .map { action ->
                    TaskStep(
                        id = UUID.randomUUID().toString(),
                        title = stepTitleFor(action),
                        detail = "Queued",
                        status = StepStatus.PENDING,
                        action = action
                    )
                }

            if (initialSteps.isNotEmpty()) {
                updateMessageSteps(assistantMsgId, initialSteps)
                executeStepSequence(assistantMsgId, initialSteps)
            }

            if (uiState.value.ttsEnabled) {
                voiceController.speak(plan.assistantReply, uiState.value.language)
            }
            _isProcessing.value = false
        }
    }

    private suspend fun executeStepSequence(messageId: Long, steps: List<TaskStep>) {
        for (step in steps) {
            updateSingleStep(messageId, step.id, StepStatus.RUNNING, "Executing…")
            delay(180L)

            when (val action = step.action) {
                is JarvisAction.LaunchApp -> {
                    val result = deviceExecutor.launchApp(action.appQuery)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.SetAlarm -> {
                    val result = deviceExecutor.setAlarm(action.hour, action.minute, action.label)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.SetTimer -> {
                    val result = deviceExecutor.setTimer(action.durationSeconds, action.label)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.CreateReminder -> {
                    val result = deviceExecutor.createReminder(action.title, action.minutesFromNow)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.SearchWeb -> {
                    val result = deviceExecutor.searchWeb(action.query)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.ToggleFlashlight -> {
                    val result = deviceExecutor.toggleFlashlight(action.enable)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.AdjustVolume -> {
                    val result = deviceExecutor.adjustVolume(action.action)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.OpenDeviceSetting -> {
                    val result = deviceExecutor.openDeviceSetting(action.settingType)
                    updateSingleStep(
                        messageId,
                        step.id,
                        if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result.message
                    )
                }

                is JarvisAction.OpenMediaPicker -> {
                    _pickerEvents.tryEmit(PickerLaunchEvent.LaunchMediaPicker(action.mediaType))
                    updateSingleStep(
                        messageId,
                        step.id,
                        StepStatus.COMPLETED,
                        "Opened Android Photo Picker (zero storage permission needed)."
                    )
                }

                is JarvisAction.OpenDocumentPicker -> {
                    _pickerEvents.tryEmit(PickerLaunchEvent.LaunchDocumentPicker)
                    updateSingleStep(
                        messageId,
                        step.id,
                        StepStatus.COMPLETED,
                        "Opened Storage Access Framework file picker."
                    )
                }

                is JarvisAction.ReadNotifications -> {
                    val accessEnabled = JarvisNotificationStore.isNotificationAccessEnabled(appContext)
                    if (!accessEnabled) {
                        updateSingleStep(
                            messageId,
                            step.id,
                            StepStatus.FAILED,
                            "Notification Access is disabled. Enable it in Vault > Permissions or Tools."
                        )
                        appendJarvisSystemMessage(
                            "Notification Access is not enabled yet. Please grant Notification Access in Android Settings so I can read notifications safely (OTPs and passwords are always masked)."
                        )
                    } else {
                        val notifs = JarvisNotificationStore.notifications.value
                        if (notifs.isEmpty()) {
                            updateSingleStep(messageId, step.id, StepStatus.COMPLETED, "No active notifications.")
                            appendJarvisSystemMessage("You have no active notifications right now.")
                        } else {
                            val summary = notifs.take(4).joinToString("\n") {
                                "• ${it.appName}: ${it.title}${if (it.body.isNotBlank()) " — ${it.body}" else ""}"
                            }
                            updateSingleStep(
                                messageId,
                                step.id,
                                StepStatus.COMPLETED,
                                "Read ${notifs.size} notification(s) with sensitive OTP masking."
                            )
                            appendJarvisSystemMessage("Recent notifications:\n$summary")
                        }
                    }
                }

                is JarvisAction.DialContact -> {
                    val (match, permissionOk) = deviceExecutor.lookupContact(action.contactQuery)
                    if (!permissionOk && match == null) {
                        updateSingleStep(
                            messageId,
                            step.id,
                            StepStatus.AWAITING_CONFIRMATION,
                            "Contacts permission missing — confirm or enter number manually."
                        )
                        enqueueConfirmation(
                            PendingConfirmation.CallConfirmation(
                                id = UUID.randomUUID().toString(),
                                title = "Confirm Phone Call",
                                description = "Contacts access is not granted. Enter or confirm the number to open the phone dialer for \"${action.contactQuery}\".",
                                displayName = action.contactQuery,
                                phoneNumber = action.contactQuery.filter { it.isDigit() || it == '+' },
                                stepId = step.id
                            )
                        )
                    } else {
                        val displayName = match?.displayName ?: action.contactQuery
                        val phone = match?.phoneNumber ?: ""
                        updateSingleStep(
                            messageId,
                            step.id,
                            StepStatus.AWAITING_CONFIRMATION,
                            "Waiting for your confirmation to dial $displayName."
                        )
                        enqueueConfirmation(
                            PendingConfirmation.CallConfirmation(
                                id = UUID.randomUUID().toString(),
                                title = "Confirm Phone Call",
                                description = if (phone.isNotBlank()) {
                                    "Open dialer to call $displayName ($phone)?"
                                } else {
                                    "No saved number matched \"$displayName\". Enter the phone number below to confirm:"
                                },
                                displayName = displayName,
                                phoneNumber = phone,
                                stepId = step.id
                            )
                        )
                    }
                }

                is JarvisAction.DraftMessage -> {
                    val (match, _) = deviceExecutor.lookupContact(action.recipientQuery)
                    val recipientName = match?.displayName ?: action.recipientQuery
                    val address = match?.phoneNumber ?: ""
                    updateSingleStep(
                        messageId,
                        step.id,
                        StepStatus.AWAITING_CONFIRMATION,
                        "Draft ready — confirm before opening ${action.platform.displayName}."
                    )
                    enqueueConfirmation(
                        PendingConfirmation.MessageConfirmation(
                            id = UUID.randomUUID().toString(),
                            title = "Confirm ${action.platform.displayName} Message",
                            description = "Review recipient and message draft before Jarvis hands off to ${action.platform.displayName}:",
                            platform = action.platform,
                            recipientName = recipientName,
                            recipientAddress = address,
                            messageBody = action.messageBody,
                            stepId = step.id
                        )
                    )
                }

                is JarvisAction.RememberPreference -> {
                    if (uiState.value.memoryConsentGranted) {
                        dao.insertMemoryFact(
                            MemoryFactEntity(
                                factKey = action.key,
                                factValue = action.value,
                                createdAtMillis = System.currentTimeMillis()
                            )
                        )
                        updateSingleStep(
                            messageId,
                            step.id,
                            StepStatus.COMPLETED,
                            "Saved locally (${action.key}: ${action.value})."
                        )
                    } else {
                        updateSingleStep(
                            messageId,
                            step.id,
                            StepStatus.AWAITING_CONFIRMATION,
                            "Consent required before saving preferences."
                        )
                        enqueueConfirmation(
                            PendingConfirmation.PreferenceConsentConfirmation(
                                id = UUID.randomUUID().toString(),
                                title = "Save Preference With Consent?",
                                description = "Jarvis only stores preferences when you allow it. Enable local preference memory and save \"${action.key}: ${action.value}\"?",
                                key = action.key,
                                value = action.value,
                                stepId = step.id
                            )
                        )
                    }
                }

                is JarvisAction.RequestSensitiveDelete -> {
                    updateSingleStep(
                        messageId,
                        step.id,
                        StepStatus.AWAITING_CONFIRMATION,
                        "Confirmation required before deleting ${action.target}."
                    )
                    enqueueConfirmation(
                        PendingConfirmation.ClearMemoryConfirmation(
                            id = UUID.randomUUID().toString(),
                            title = "Confirm Deletion",
                            description = "Are you sure you want to permanently delete ${action.target}? This cannot be undone.",
                            stepId = step.id
                        )
                    )
                }

                is JarvisAction.RespondOnly -> Unit
            }
        }
    }

    fun confirmPendingAction(
        confirmation: PendingConfirmation,
        editedNumberOrAddress: String? = null,
        editedMessageBody: String? = null
    ) {
        viewModelScope.launch {
            _pendingConfirmations.update { list -> list.filterNot { it.id == confirmation.id } }
            when (confirmation) {
                is PendingConfirmation.CallConfirmation -> {
                    val number = editedNumberOrAddress?.trim().orEmpty().ifEmpty { confirmation.phoneNumber }
                    val res = deviceExecutor.executeDial(number)
                    confirmation.stepId?.let {
                        markStepEverywhere(it, if (res.success) StepStatus.COMPLETED else StepStatus.FAILED, res.message)
                    }
                    appendJarvisSystemMessage(res.message)
                }

                is PendingConfirmation.MessageConfirmation -> {
                    val addr = editedNumberOrAddress?.trim().orEmpty().ifEmpty { confirmation.recipientAddress }
                    val body = editedMessageBody?.trim().orEmpty().ifEmpty { confirmation.messageBody }
                    val res = deviceExecutor.executeSendMessage(confirmation.platform, addr, body)
                    confirmation.stepId?.let {
                        markStepEverywhere(it, if (res.success) StepStatus.COMPLETED else StepStatus.FAILED, res.message)
                    }
                    appendJarvisSystemMessage(res.message)
                }

                is PendingConfirmation.PreferenceConsentConfirmation -> {
                    prefsRepo.setMemoryConsent(true)
                    dao.insertMemoryFact(
                        MemoryFactEntity(
                            factKey = confirmation.key,
                            factValue = confirmation.value,
                            createdAtMillis = System.currentTimeMillis()
                        )
                    )
                    confirmation.stepId?.let {
                        markStepEverywhere(it, StepStatus.COMPLETED, "Consent granted & preference saved.")
                    }
                    appendJarvisSystemMessage("Got it. I have remembered \"${confirmation.key}: ${confirmation.value}\" locally.")
                }

                is PendingConfirmation.ClearMemoryConfirmation -> {
                    dao.clearAllMemoryFacts()
                    dao.clearMessages()
                    confirmation.stepId?.let {
                        markStepEverywhere(it, StepStatus.COMPLETED, "Deleted local records.")
                    }
                    _statusToast.value = "Local memory cleared."
                }
            }
        }
    }

    fun cancelPendingAction(confirmation: PendingConfirmation) {
        _pendingConfirmations.update { list -> list.filterNot { it.id == confirmation.id } }
        confirmation.stepId?.let {
            markStepEverywhere(it, StepStatus.CANCELLED, "Cancelled by user.")
        }
        viewModelScope.launch {
            appendJarvisSystemMessage("Action cancelled. No changes were executed.")
        }
    }

    private fun enqueueConfirmation(confirmation: PendingConfirmation) {
        _pendingConfirmations.update { it + confirmation }
    }

    private fun updateMessageSteps(messageId: Long, steps: List<TaskStep>) {
        _activeStepsByMessageId.update { current -> current + (messageId to steps) }
    }

    private fun updateSingleStep(messageId: Long, stepId: String, status: StepStatus, detail: String) {
        _activeStepsByMessageId.update { current ->
            val existing = current[messageId] ?: return@update current
            val updated = existing.map {
                if (it.id == stepId) it.copy(status = status, detail = detail) else it
            }
            current + (messageId to updated)
        }
    }

    private fun markStepEverywhere(stepId: String, status: StepStatus, detail: String) {
        _activeStepsByMessageId.update { current ->
            current.mapValues { (_, steps) ->
                steps.map { if (it.id == stepId) it.copy(status = status, detail = detail) else it }
            }
        }
    }

    private suspend fun appendJarvisSystemMessage(text: String) {
        dao.insertMessage(
            MessageEntity(
                isUser = false,
                text = text,
                timestampMillis = System.currentTimeMillis()
            )
        )
        if (uiState.value.ttsEnabled) {
            voiceController.speak(text, uiState.value.language)
        }
    }

    private fun stepTitleFor(action: JarvisAction): String = when (action) {
        is JarvisAction.LaunchApp -> "Launch ${action.appQuery}"
        is JarvisAction.DialContact -> "Call ${action.contactQuery}"
        is JarvisAction.DraftMessage -> "Draft ${action.platform.displayName} message"
        is JarvisAction.SetAlarm -> String.format(
            java.util.Locale.US,
            "Set alarm %02d:%02d",
            action.hour,
            action.minute
        )
        is JarvisAction.SetTimer -> "Set ${action.durationSeconds}s timer"
        is JarvisAction.CreateReminder -> "Create reminder"
        is JarvisAction.SearchWeb -> "Web search: ${action.query}"
        is JarvisAction.OpenMediaPicker -> "Open Photo Picker"
        is JarvisAction.OpenDocumentPicker -> "Open Document Picker"
        is JarvisAction.ReadNotifications -> "Read notifications"
        is JarvisAction.ToggleFlashlight -> when (action.enable) {
            true -> "Turn Flashlight ON"
            false -> "Turn Flashlight OFF"
            null -> "Toggle Flashlight"
        }
        is JarvisAction.AdjustVolume -> action.action.displayName
        is JarvisAction.OpenDeviceSetting -> "Open ${action.settingType.displayName}"
        is JarvisAction.RememberPreference -> "Save preference: ${action.key}"
        is JarvisAction.RequestSensitiveDelete -> "Delete ${action.target}"
        is JarvisAction.RespondOnly -> "Response"
    }

    // Direct Tool Actions from Tools & Vault screens
    fun toggleFlashlightDirect() {
        val result = deviceExecutor.toggleFlashlight(null)
        _statusToast.value = result.message
    }

    fun setMediaVolumeDirect(percent: Int) {
        deviceExecutor.setMediaVolumePercent(percent)
    }

    fun openSettingDirect(type: DeviceSettingType) {
        val result = deviceExecutor.openDeviceSetting(type)
        if (!result.success) _statusToast.value = result.message
    }

    fun launchInstalledAppDirect(app: InstalledAppInfo) {
        val result = deviceExecutor.launchAppByPackage(app.packageName, app.label)
        _statusToast.value = result.message
    }

    fun requestPhotoPicker(type: MediaPickType) {
        _pickerEvents.tryEmit(PickerLaunchEvent.LaunchMediaPicker(type))
    }

    fun requestDocumentPicker() {
        _pickerEvents.tryEmit(PickerLaunchEvent.LaunchDocumentPicker)
    }

    fun onMediaUriPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) { readUriMetadata(uri) }
            _selectedFiles.update { list -> (listOf(item) + list).take(10) }
            _statusToast.value = "Selected ${item.displayName}"
        }
    }

    private fun readUriMetadata(uri: Uri): SelectedMediaItem {
        var name = "Selected File"
        var size = 0L
        val mime = appContext.contentResolver.getType(uri) ?: "application/octet-stream"
        runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return SelectedMediaItem(
            uriString = uri.toString(),
            displayName = name,
            mimeType = mime,
            sizeBytes = size
        )
    }

    fun readNotificationsAloud() {
        val notifs = uiState.value.recentNotifications
        if (notifs.isEmpty()) {
            _statusToast.value = "No active notifications to read."
            return
        }
        val speech = notifs.take(3).joinToString(". ") {
            "From ${it.appName}: ${it.title}. ${it.body}"
        }
        voiceController.speak(speech, uiState.value.language)
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch {
            dao.deleteMemoryFact(id)
        }
    }

    fun requestClearAllMemory() {
        enqueueConfirmation(
            PendingConfirmation.ClearMemoryConfirmation(
                id = UUID.randomUUID().toString(),
                title = "Clear All Memory & Chat?",
                description = "This removes all saved preference facts and conversation history from this device."
            )
        )
    }

    fun saveManualPreferenceFact(key: String, value: String) {
        val cleanKey = key.trim()
        val cleanVal = value.trim()
        if (cleanKey.isEmpty() || cleanVal.isEmpty()) return
        viewModelScope.launch {
            if (uiState.value.memoryConsentGranted) {
                dao.insertMemoryFact(
                    MemoryFactEntity(
                        factKey = cleanKey,
                        factValue = cleanVal,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
                _statusToast.value = "Preference saved."
            } else {
                enqueueConfirmation(
                    PendingConfirmation.PreferenceConsentConfirmation(
                        id = UUID.randomUUID().toString(),
                        title = "Enable Memory & Save?",
                        description = "Allow Jarvis to remember \"$cleanKey: $cleanVal\" locally?",
                        key = cleanKey,
                        value = cleanVal
                    )
                )
            }
        }
    }
}
