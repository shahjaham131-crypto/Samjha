package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.StepStatus
import com.example.domain.model.TaskStep
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.TealSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisArcCoreHeader(
    isListening: Boolean,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    rmsLevel: Float,
    partialTranscript: String,
    modifier: Modifier = Modifier,
    onCoreClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_orbit")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 4000 else 16000, easing = LinearEasing)
        ),
        label = "outer_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking || isProcessing) 3200 else 11000, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val animatedRms by animateFloatAsState(
        targetValue = if (isListening) rmsLevel else if (isSpeaking) 0.55f else 0.1f,
        animationSpec = spring(),
        label = "rms_anim"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceBg = MaterialTheme.colorScheme.background

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1794828921033),
                contentDescription = "Jarvis HUD command deck backdrop",
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.24f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                surfaceBg.copy(alpha = 0.4f),
                                surfaceBg.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCoreClick)
                        .testTag("jarvis_arc_core"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.25f + animatedRms * 0.25f),
                                    primaryColor.copy(alpha = 0.0f)
                                ),
                                center = center,
                                radius = radius
                            ),
                            radius = radius
                        )

                        rotate(outerAngle, pivot = center) {
                            val arcRadius = radius * 0.88f
                            drawArc(
                                color = if (isListening) tertiaryColor else primaryColor,
                                startAngle = 0f,
                                sweepAngle = 115f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = Size(arcRadius * 2, arcRadius * 2),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = secondaryColor.copy(alpha = 0.7f),
                                startAngle = 165f,
                                sweepAngle = 135f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = Size(arcRadius * 2, arcRadius * 2),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        rotate(innerAngle, pivot = center) {
                            val innerRadius = radius * 0.64f
                            for (i in 0 until 12) {
                                val deg = i * 30.0
                                val rad = deg * PI / 180.0
                                val startR = innerRadius * 0.85f
                                val endR = innerRadius * (1.0f + animatedRms * 0.16f)
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.75f),
                                    start = Offset(
                                        center.x + (cos(rad) * startR).toFloat(),
                                        center.y + (sin(rad) * startR).toFloat()
                                    ),
                                    end = Offset(
                                        center.x + (cos(rad) * endR).toFloat(),
                                        center.y + (sin(rad) * endR).toFloat()
                                    ),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        val coreRadius = radius * 0.32f * pulse * (1f + animatedRms * 0.25f)
                        drawCircle(
                            color = if (isListening) tertiaryColor else primaryColor,
                            radius = coreRadius,
                            center = center
                        )
                        drawCircle(
                            color = surfaceBg,
                            radius = coreRadius * 0.45f,
                            center = center
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "JARVIS AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val statusLabel = when {
                            isListening -> "LISTENING"
                            isProcessing -> "EXECUTING"
                            isSpeaking -> "SPEAKING"
                            else -> "ONLINE"
                        }
                        val badgeColor = when {
                            isListening -> AmberAccent
                            isProcessing -> TealSecondary
                            isSpeaking -> CyanPrimary
                            else -> EmeraldSuccess
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = when {
                            partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                            isListening -> "Speak in English, Hindi, or Hinglish…"
                            isProcessing -> "Coordinating authorized Android tools…"
                            else -> "Personal Android automation & voice command core"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
fun SampleAiNoticeBanner(
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDetails = true }
            .testTag("sample_ai_notice_banner"),
        color = AmberContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Sample AI information",
                tint = AmberAccent,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.firebase_notice_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberAccent
                )
                Text(
                    text = stringResource(R.string.firebase_notice_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = AmberAccent
                )
            },
            title = { Text("Firebase AI Logic Required for Live Gemini") },
            text = {
                Text(
                    "Firebase setup was declined, so generative AI answers use clearly labeled sample responses. " +
                        "All native Android device integrations (launching apps, alarms, timers, call/message drafts, " +
                        "flashlight, volume, photo/file pickers, and notification reading) operate directly on your phone."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
fun TaskStepProgressCard(
    steps: List<TaskStep>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = steps.isNotEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "EXECUTION PIPELINE (${steps.size} STEP${if (steps.size > 1) "S" else ""})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (step.status) {
                            StepStatus.PENDING -> Icon(
                                imageVector = Icons.Filled.HourglassTop,
                                contentDescription = "Pending",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            StepStatus.RUNNING -> CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyanPrimary
                            )
                            StepStatus.AWAITING_CONFIRMATION -> Icon(
                                imageVector = Icons.Filled.PendingActions,
                                contentDescription = "Awaiting confirmation",
                                tint = AmberAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            StepStatus.COMPLETED -> Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Completed",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            StepStatus.FAILED -> Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Failed",
                                tint = RoseError,
                                modifier = Modifier.size(18.dp)
                            )
                            StepStatus.CANCELLED -> Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = "Cancelled",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. ${step.title}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = step.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyConfirmationDialog(
    confirmation: PendingConfirmation,
    onConfirm: (editedAddress: String?, editedBody: String?) -> Unit,
    onCancel: () -> Unit
) {
    var addressDraft by remember(confirmation.id) {
        mutableStateOf(
            when (confirmation) {
                is PendingConfirmation.CallConfirmation -> confirmation.phoneNumber
                is PendingConfirmation.MessageConfirmation -> confirmation.recipientAddress
                else -> ""
            }
        )
    }
    var bodyDraft by remember(confirmation.id) {
        mutableStateOf(
            if (confirmation is PendingConfirmation.MessageConfirmation) confirmation.messageBody else ""
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Safety confirmation",
                tint = AmberAccent
            )
        },
        title = {
            Text(
                text = confirmation.title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = confirmation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (confirmation) {
                    is PendingConfirmation.CallConfirmation -> {
                        OutlinedTextField(
                            value = addressDraft,
                            onValueChange = { addressDraft = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_call_number_input")
                        )
                    }

                    is PendingConfirmation.MessageConfirmation -> {
                        Text(
                            text = "Recipient: ${confirmation.recipientName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = addressDraft,
                            onValueChange = { addressDraft = it },
                            label = { Text("Phone / Address (optional for WhatsApp picker)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_message_address_input")
                        )
                        OutlinedTextField(
                            value = bodyDraft,
                            onValueChange = { bodyDraft = it },
                            label = { Text("Message Draft") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_message_body_input")
                        )
                    }

                    is PendingConfirmation.PreferenceConsentConfirmation -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.small
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${confirmation.key}: ${confirmation.value}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    is PendingConfirmation.ClearMemoryConfirmation -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(addressDraft, bodyDraft) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_sensitive_action_button")
            ) {
                Text(
                    text = stringResource(R.string.confirm_button),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_sensitive_action_button")
            ) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}
