package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.domain.model.DeviceSettingType
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.MediaPickType
import com.example.ui.JarvisUiState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldSuccess

@Composable
fun ToolsDeckScreen(
    state: JarvisUiState,
    onToggleFlashlight: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onOpenSetting: (DeviceSettingType) -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onRefreshApps: () -> Unit,
    onPickMedia: (MediaPickType) -> Unit,
    onPickDocument: () -> Unit,
    onReadNotificationsAloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "AUTHORIZED TOOLS & CONTROLS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Device Command Deck",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. Hardware & Settings Control Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Hardware & System Controls",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onToggleFlashlight,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (state.flashlightOn) {
                                    AmberAccent.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("tool_flashlight_button")
                        ) {
                            Icon(
                                imageVector = if (state.flashlightOn) {
                                    Icons.Filled.FlashlightOn
                                } else {
                                    Icons.Filled.FlashlightOff
                                },
                                contentDescription = "Toggle flashlight",
                                tint = if (state.flashlightOn) AmberAccent else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.flashlightOn) "Torch ON" else "Torch OFF")
                        }

                        OutlinedButton(
                            onClick = { onOpenSetting(DeviceSettingType.GENERAL_SETTINGS) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("tool_system_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Settings")
                        }
                    }

                    // Real Media Volume Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Media volume",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Media Volume",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "${state.mediaVolumePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = state.mediaVolumePercent.toFloat(),
                            onValueChange = { onVolumeChange(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("tool_volume_slider")
                        )
                    }

                    // Quick Settings Panels
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quickSettings = listOf(
                            Triple(DeviceSettingType.WIFI, "Wi-Fi", Icons.Filled.Wifi),
                            Triple(DeviceSettingType.BLUETOOTH, "Bluetooth", Icons.Filled.Bluetooth),
                            Triple(DeviceSettingType.DISPLAY, "Display", Icons.Filled.Brightness6),
                            Triple(DeviceSettingType.BATTERY, "Battery", Icons.Filled.BatterySaver),
                            Triple(DeviceSettingType.LOCATION, "Location", Icons.Filled.LocationOn)
                        )
                        items(quickSettings) { (type, label, icon) ->
                            QuickSettingChip(
                                label = label,
                                icon = icon,
                                onClick = { onOpenSetting(type) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Installed Apps Quick Launcher
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = "Installed apps",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Installed Apps (${state.installedApps.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(
                            onClick = onRefreshApps,
                            modifier = Modifier.testTag("refresh_apps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh installed apps"
                            )
                        }
                    }

                    if (state.installedApps.isEmpty()) {
                        Text(
                            text = "No external launchable apps detected yet. Tap refresh or say \"Open YouTube\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.installedApps.take(18), key = { it.packageName }) { app ->
                                AppLauncherTile(
                                    app = app,
                                    onClick = { onLaunchApp(app) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Play-Compliant Files, Photos & Documents
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Photos & Documents (Zero-Permission Picker)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Uses Android Photo Picker & Storage Access Framework without requesting broad storage access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPickMedia(MediaPickType.IMAGE) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("pick_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Photo Picker")
                        }
                        OutlinedButton(
                            onClick = onPickDocument,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("pick_document_button")
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Documents")
                        }
                    }

                    if (state.selectedFiles.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.selectedFiles.take(4).forEach { file ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${(file.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Explicit-Consent Notification Reader
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Notifications",
                                tint = if (state.permissions.notificationListenerEnabled) {
                                    EmeraldSuccess
                                } else {
                                    AmberAccent
                                }
                            )
                            Text(
                                text = "Notification Reader",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (state.permissions.notificationListenerEnabled && state.recentNotifications.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = onReadNotificationsAloud,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("read_notifications_aloud_button")
                            ) {
                                Text("Read Aloud")
                            }
                        }
                    }

                    if (!state.permissions.notificationListenerEnabled) {
                        Text(
                            text = "Notification Access is disabled. Jarvis only reads notifications after you explicitly allow access in Android Settings. OTPs and passwords are automatically masked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { onOpenSetting(DeviceSettingType.NOTIFICATION_ACCESS) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("enable_notification_access_button")
                        ) {
                            Text("Open Notification Access Settings")
                        }
                    } else if (state.recentNotifications.isEmpty()) {
                        Text(
                            text = "Notification Access active. No active notifications to show right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.recentNotifications.take(5).forEach { notif ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${notif.appName} • ${notif.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (notif.body.isNotBlank()) {
                                        Text(
                                            text = notif.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSettingChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("quick_setting_${label.lowercase()}"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun AppLauncherTile(
    app: InstalledAppInfo,
    onClick: () -> Unit
) {
    val bitmap = remember(app.packageName) {
        runCatching {
            app.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
        }.getOrNull()
    }

    Surface(
        modifier = Modifier
            .width(86.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("launch_app_${app.packageName}"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = app.label,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
