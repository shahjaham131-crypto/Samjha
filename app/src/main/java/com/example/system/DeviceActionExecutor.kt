package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.domain.model.DeviceSettingType
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.MessagePlatform
import com.example.domain.model.VolumeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

data class ActionExecutionResult(
    val success: Boolean,
    val message: String,
    val missingPermission: String? = null
)

data class ContactMatch(
    val displayName: String,
    val phoneNumber: String
)

class DeviceActionExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _flashlightOn = MutableStateFlow(false)
    val flashlightOn: StateFlow<Boolean> = _flashlightOn.asStateFlow()

    private val _mediaVolumePercent = MutableStateFlow(readMediaVolumePercent())
    val mediaVolumePercent: StateFlow<Int> = _mediaVolumePercent.asStateFlow()

    private var torchCameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _flashlightOn.value = enabled
            }
        }
    }

    init {
        torchCameraId = findTorchCameraId()
        if (torchCameraId != null) {
            runCatching {
                cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            }
        }
    }

    fun release() {
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
    }

    private fun findTorchCameraId(): String? {
        val manager = cameraManager ?: return null
        return runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        }.getOrDefault(emptyList())

        val priorityPackages = listOf(
            "com.google.android.youtube",
            "com.whatsapp",
            "com.instagram.android",
            "com.facebook.katana",
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.spotify.music",
            "com.android.settings"
        )

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo?.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                InstalledAppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    icon = runCatching { info.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedWith(
                compareBy<InstalledAppInfo> {
                    val idx = priorityPackages.indexOf(it.packageName)
                    if (idx >= 0) idx else Int.MAX_VALUE
                }.thenBy { it.label.lowercase() }
            )
            .toList()
    }

    fun launchApp(appQuery: String): ActionExecutionResult {
        val cleanQuery = appQuery.trim()
        if (cleanQuery.isEmpty()) {
            return ActionExecutionResult(false, "No application name was specified.")
        }

        val knownPackageAliases = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "play store" to "com.android.vending",
            "settings" to "com.android.settings",
            "gmail" to "com.google.android.gm"
        )

        val pm = context.packageManager
        val aliasPkg = knownPackageAliases[cleanQuery.lowercase()]
        if (aliasPkg != null) {
            pm.getLaunchIntentForPackage(aliasPkg)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return startActivitySafely(intent, "Opened $cleanQuery.")
            }
        }

        val installed = getInstalledLaunchableApps()
        val match = installed.firstOrNull { it.label.equals(cleanQuery, ignoreCase = true) }
            ?: installed.firstOrNull { it.label.contains(cleanQuery, ignoreCase = true) }
            ?: installed.firstOrNull { cleanQuery.contains(it.label, ignoreCase = true) }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return startActivitySafely(launchIntent, "Launched ${match.label}.")
            }
        }

        return ActionExecutionResult(
            success = false,
            message = "Could not find \"$cleanQuery\" installed on this device."
        )
    }

    fun launchAppByPackage(packageName: String, label: String): ActionExecutionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionExecutionResult(false, "$label is not launchable on this device.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startActivitySafely(intent, "Opened $label.")
    }

    fun lookupContact(query: String): Pair<ContactMatch?, Boolean> {
        val trimmed = query.trim()
        // If user already supplied digits/phone number, accept directly
        val digitsOnly = trimmed.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 6 && trimmed.count { it.isLetter() } == 0) {
            return ContactMatch(displayName = trimmed, phoneNumber = digitsOnly) to true
        }

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            return null to false
        }

        val cursor = runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$trimmed%"),
                null
            )
        }.getOrNull()

        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(0) ?: trimmed
                val number = it.getString(1) ?: ""
                if (number.isNotBlank()) {
                    return ContactMatch(name, number) to true
                }
            }
        }
        return null to true
    }

    fun executeDial(phoneNumber: String): ActionExecutionResult {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) {
            return ActionExecutionResult(false, "Phone number is empty.")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(intent, "Opened phone dialer for $clean.")
    }

    fun executeSendMessage(
        platform: MessagePlatform,
        recipientAddress: String,
        body: String
    ): ActionExecutionResult {
        return when (platform) {
            MessagePlatform.SMS -> {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${Uri.encode(recipientAddress)}")
                    putExtra("sms_body", body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivitySafely(intent, "Opened SMS draft for $recipientAddress.")
            }

            MessagePlatform.WHATSAPP -> {
                val cleanPhone = recipientAddress.filter { it.isDigit() }
                val waInstalled = context.packageManager.getLaunchIntentForPackage("com.whatsapp") != null
                if (cleanPhone.length >= 7) {
                    val encoded = URLEncoder.encode(body, "UTF-8")
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/$cleanPhone?text=$encoded")
                    ).apply {
                        if (waInstalled) setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivitySafely(intent, "Opened WhatsApp message draft.")
                } else {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, body)
                        if (waInstalled) setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivitySafely(
                        if (waInstalled) shareIntent else Intent.createChooser(shareIntent, "Send message")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        "Opened message composer with draft text."
                    )
                }
            }
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): ActionExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val timeText = String.format(java.util.Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return startActivitySafely(intent, "Alarm intent launched for $timeText ($label).")
    }

    fun setTimer(seconds: Int, label: String): ActionExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(intent, "Timer intent launched for ${seconds}s.")
    }

    fun createReminder(title: String, minutesFromNow: Int): ActionExecutionResult {
        val startMillis = System.currentTimeMillis() + minutesFromNow * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 30 * 60_000L)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(intent, "Opened calendar reminder for \"$title\".")
    }

    fun searchWeb(query: String): ActionExecutionResult {
        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val firstTry = startActivitySafely(webSearchIntent, "Opened web search for \"$query\".")
        if (firstTry.success) return firstTry

        val encoded = URLEncoder.encode(query, "UTF-8")
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=$encoded")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(browserIntent, "Opened browser search for \"$query\".")
    }

    fun toggleFlashlight(enable: Boolean?): ActionExecutionResult {
        val id = torchCameraId ?: findTorchCameraId()
        val manager = cameraManager
        if (id == null || manager == null) {
            return ActionExecutionResult(false, "No camera flash hardware is available on this device.")
        }
        torchCameraId = id
        val target = enable ?: !_flashlightOn.value
        return runCatching {
            manager.setTorchMode(id, target)
            _flashlightOn.value = target
            ActionExecutionResult(true, if (target) "Flashlight turned ON." else "Flashlight turned OFF.")
        }.getOrElse { error ->
            ActionExecutionResult(
                false,
                "Could not toggle flashlight: ${error.localizedMessage ?: "Hardware busy"}"
            )
        }
    }

    fun adjustVolume(action: VolumeAction): ActionExecutionResult {
        val manager = audioManager ?: return ActionExecutionResult(false, "Audio service unavailable.")
        runCatching {
            when (action) {
                VolumeAction.UP -> manager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                VolumeAction.DOWN -> manager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                VolumeAction.MUTE -> manager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                VolumeAction.UNMUTE -> manager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            _mediaVolumePercent.value = readMediaVolumePercent()
        }.onFailure {
            return ActionExecutionResult(false, "Unable to change volume: ${it.localizedMessage}")
        }
        return ActionExecutionResult(true, "${action.displayName} (${_mediaVolumePercent.value}%).")
    }

    fun setMediaVolumePercent(percent: Int): ActionExecutionResult {
        val manager = audioManager ?: return ActionExecutionResult(false, "Audio service unavailable.")
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetIndex = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
        return runCatching {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            _mediaVolumePercent.value = readMediaVolumePercent()
            ActionExecutionResult(true, "Media volume set to ${_mediaVolumePercent.value}%.")
        }.getOrElse {
            ActionExecutionResult(false, "Cannot modify volume: ${it.localizedMessage}")
        }
    }

    fun readMediaVolumePercent(): Int {
        val manager = audioManager ?: return 50
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun openDeviceSetting(type: DeviceSettingType): ActionExecutionResult {
        val intentAction = when (type) {
            DeviceSettingType.WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
            DeviceSettingType.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            DeviceSettingType.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            DeviceSettingType.SOUND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_VOLUME
            } else {
                Settings.ACTION_SOUND_SETTINGS
            }
            DeviceSettingType.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            DeviceSettingType.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            DeviceSettingType.NOTIFICATION_ACCESS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            DeviceSettingType.GENERAL_SETTINGS -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(intentAction).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val result = startActivitySafely(intent, "Opened ${type.displayName} settings.")
        if (!result.success && type != DeviceSettingType.GENERAL_SETTINGS) {
            val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return startActivitySafely(fallback, "Opened System Settings.")
        }
        return result
    }

    private fun startActivitySafely(intent: Intent, successMessage: String): ActionExecutionResult {
        return runCatching {
            context.startActivity(intent)
            ActionExecutionResult(true, successMessage)
        }.getOrElse { error ->
            ActionExecutionResult(
                false,
                "Could not open target activity: ${error.localizedMessage ?: "No matching app installed"}"
            )
        }
    }
}
