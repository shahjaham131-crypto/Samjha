package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DeviceSettingType
import com.example.ui.JarvisUiState
import com.example.ui.components.SampleAiNoticeBanner
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun VaultSettingsScreen(
    state: JarvisUiState,
    onToggleMemoryConsent: (Boolean) -> Unit,
    onSaveManualFact: (String, String) -> Unit,
    onDeleteFact: (Long) -> Unit,
    onRequestClearAll: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onOpenSetting: (DeviceSettingType) -> Unit,
    modifier: Modifier = Modifier
) {
    var factKeyDraft by remember { mutableStateOf("") }
    var factValueDraft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "PRIVACY, CONSENT & TELEMETRY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Memory & Permissions Vault",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (!state.isCloudAiConnected) {
            item {
                SampleAiNoticeBanner()
            }
        }

        // 1. Consented Memory Card
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
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = "Memory consent",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.memory_consent_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.memory_consent_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.memoryConsentGranted,
                            onCheckedChange = onToggleMemoryConsent,
                            modifier = Modifier.testTag("memory_consent_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = factKeyDraft,
                            onValueChange = { factKeyDraft = it },
                            label = { Text("Preference (e.g. Name)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("memory_key_input")
                        )
                        OutlinedTextField(
                            value = factValueDraft,
                            onValueChange = { factValueDraft = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("memory_value_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (factKeyDraft.isNotBlank() && factValueDraft.isNotBlank()) {
                                    onSaveManualFact(factKeyDraft, factValueDraft)
                                    factKeyDraft = ""
                                    factValueDraft = ""
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_preference_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Preference")
                        }
                        OutlinedButton(
                            onClick = onRequestClearAll,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_all_memory_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.clear_memory_button))
                        }
                    }

                    if (state.rememberedFacts.isEmpty()) {
                        Text(
                            text = "No personal preferences stored yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.rememberedFacts.forEach { fact ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fact.factKey,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = fact.factValue,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteFact(fact.id) },
                                            modifier = Modifier.testTag("delete_fact_${fact.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete preference",
                                                tint = RoseError
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

        // 2. Permissions Matrix
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Permissions",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Android Permissions & Access",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    PermissionRow(
                        title = "Microphone (Voice Commands)",
                        description = "Required for live English, Hindi, and Hinglish speech recognition.",
                        granted = state.permissions.microphoneGranted,
                        icon = Icons.Filled.Mic,
                        actionLabel = "Grant",
                        tag = "grant_mic_permission",
                        onAction = onRequestMicPermission
                    )

                    PermissionRow(
                        title = "Contacts (Dial & Message Lookup)",
                        description = "Optional: resolves contact names before asking confirmation to call or message.",
                        granted = state.permissions.contactsGranted,
                        icon = Icons.Filled.Contacts,
                        actionLabel = "Grant",
                        tag = "grant_contacts_permission",
                        onAction = onRequestContactsPermission
                    )

                    PermissionRow(
                        title = "Notifications Posting",
                        description = "Allows Jarvis status & reminder notifications on Android 13+.",
                        granted = state.permissions.postNotificationsGranted,
                        icon = Icons.Filled.Notifications,
                        actionLabel = "Grant",
                        tag = "grant_post_notif_permission",
                        onAction = onRequestNotificationsPermission
                    )

                    PermissionRow(
                        title = "Notification Listener Access",
                        description = "Explicit system setting so Jarvis can summarize active alerts (OTPs masked).",
                        granted = state.permissions.notificationListenerEnabled,
                        icon = Icons.Filled.Shield,
                        actionLabel = "Configure",
                        tag = "configure_notif_listener",
                        onAction = { onOpenSetting(DeviceSettingType.NOTIFICATION_ACCESS) }
                    )
                }
            }
        }

        // 3. Safety & Privacy Principles Card
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Jarvis Safety & Security Protocol",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Always confirms before dialing calls, sending messages, or deleting records.\n" +
                            "• Never bypasses Android authentication, app locks, or runtime permissions.\n" +
                            "• Automatically redacts OTPs, PINs, and passwords when reading notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    icon: ImageVector,
    actionLabel: String,
    tag: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) EmeraldSuccess else AmberAccent,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Granted",
                    tint = EmeraldSuccess
                )
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(tag)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
