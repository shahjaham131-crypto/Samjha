package com.example

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.MediaPickType
import com.example.ui.JarvisViewModel
import com.example.ui.PickerLaunchEvent
import com.example.ui.components.SafetyConfirmationDialog
import com.example.ui.screens.CommandHubScreen
import com.example.ui.screens.ToolsDeckScreen
import com.example.ui.screens.VaultSettingsScreen
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.ObsidianBg
import kotlinx.coroutines.launch

private enum class JarvisRoute(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HUB("hub", R.string.nav_hub, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    TOOLS("tools", R.string.nav_tools, Icons.Filled.Build, Icons.Outlined.Build),
    VAULT("vault", R.string.nav_vault, Icons.Filled.Security, Icons.Outlined.Security)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(ObsidianBg.toArgb())
        )
        setContent {
            JarvisTheme {
                JarvisRootApp()
            }
        }
    }
}

@Composable
fun JarvisRootApp(
    viewModel: JarvisViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: JarvisRoute.HUB.route
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var micDeniedOnce by remember { mutableStateOf(false) }
    var showMicPermissionDialog by remember { mutableStateOf(false) }

    // Refresh permission status whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val speechFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                viewModel.submitCommand(spoken)
            }
        }
    }

    val startVoiceOrFallback: () -> Unit = {
        if (viewModel.isSpeechRecognitionAvailable()) {
            viewModel.startVoiceInput()
        } else {
            try {
                speechFallbackLauncher.launch(viewModel.buildFallbackSpeechIntent())
            } catch (_: ActivityNotFoundException) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Speech recognition service is not installed on this device. Please type your command."
                    )
                }
            }
        }
    }

    // Runtime permission launchers
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            micDeniedOnce = false
            showMicPermissionDialog = false
            startVoiceOrFallback()
        } else {
            micDeniedOnce = true
            showMicPermissionDialog = true
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    // Zero-permission Photo Picker & Storage Access Framework launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onMediaUriPicked(uri)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.onMediaUriPicked(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.pickerEvents.collect { event ->
            when (event) {
                is PickerLaunchEvent.LaunchMediaPicker -> {
                    val mediaType = when (event.mediaType) {
                        MediaPickType.IMAGE -> ActivityResultContracts.PickVisualMedia.ImageOnly
                        MediaPickType.VIDEO -> ActivityResultContracts.PickVisualMedia.VideoOnly
                        MediaPickType.ANY_MEDIA -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    }
                    photoPickerLauncher.launch(PickVisualMediaRequest(mediaType))
                }
                PickerLaunchEvent.LaunchDocumentPicker -> {
                    documentPickerLauncher.launch(arrayOf("*/*"))
                }
            }
        }
    }

    LaunchedEffect(state.statusToast) {
        val msg = state.statusToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeStatusToast()
    }

    val requestMicWithRationale: () -> Unit = {
        val activity = context as? Activity
        val showRationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO
        )
        if (showRationale || micDeniedOnce) {
            showMicPermissionDialog = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val onToggleVoice: () -> Unit = {
        if (state.isListening) {
            viewModel.stopVoiceInput()
        } else if (!state.permissions.microphoneGranted) {
            requestMicWithRationale()
        } else {
            startVoiceOrFallback()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        JarvisRoute.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(JarvisRoute.HUB.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        JarvisRoute.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(JarvisRoute.HUB.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = JarvisRoute.HUB.route,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    composable(JarvisRoute.HUB.route) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            CommandHubScreen(
                                state = state,
                                onDraftChange = viewModel::updateInputDraft,
                                onSendCommand = viewModel::submitCommand,
                                onToggleVoice = onToggleVoice,
                                onLanguageSelected = viewModel::setLanguage,
                                onToggleTts = viewModel::setTtsEnabled,
                                onStopSpeaking = viewModel::stopSpeaking,
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }

                    composable(JarvisRoute.TOOLS.route) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ToolsDeckScreen(
                                state = state,
                                onToggleFlashlight = viewModel::toggleFlashlightDirect,
                                onVolumeChange = viewModel::setMediaVolumeDirect,
                                onOpenSetting = viewModel::openSettingDirect,
                                onLaunchApp = viewModel::launchInstalledAppDirect,
                                onRefreshApps = viewModel::refreshInstalledApps,
                                onPickMedia = viewModel::requestPhotoPicker,
                                onPickDocument = viewModel::requestDocumentPicker,
                                onReadNotificationsAloud = viewModel::readNotificationsAloud,
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }

                    composable(JarvisRoute.VAULT.route) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            VaultSettingsScreen(
                                state = state,
                                onToggleMemoryConsent = viewModel::setMemoryConsent,
                                onSaveManualFact = viewModel::saveManualPreferenceFact,
                                onDeleteFact = viewModel::deleteMemoryFact,
                                onRequestClearAll = viewModel::requestClearAllMemory,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onRequestContactsPermission = {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                onRequestNotificationsPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onOpenSetting = viewModel::openSettingDirect,
                                modifier = Modifier.widthIn(max = 840.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Active Safety Confirmation Modal (Calls, Messages, Consent, Delete)
    state.pendingConfirmations.firstOrNull()?.let { pending ->
        SafetyConfirmationDialog(
            confirmation = pending,
            onConfirm = { editedAddr, editedBody ->
                viewModel.confirmPendingAction(pending, editedAddr, editedBody)
            },
            onCancel = {
                viewModel.cancelPendingAction(pending)
            }
        )
    }

    // Microphone Permission Rationale / Settings Dialog
    if (showMicPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showMicPermissionDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Microphone permission",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Microphone Permission Required") },
            text = {
                Text(
                    "Jarvis AI uses Android SpeechRecognizer to listen to your English, Hindi, and Hinglish voice commands. " +
                        "Please grant Microphone permission or enable it in App Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicPermissionDialog = false
                        val activity = context as? Activity
                        val canAskAgain = activity == null || ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.RECORD_AUDIO
                        )
                        if (canAskAgain) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(settingsIntent)
                        }
                    },
                    modifier = Modifier.testTag("mic_permission_dialog_confirm")
                ) {
                    Text(if (micDeniedOnce) "Allow / Open Settings" else "Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMicPermissionDialog = false },
                    modifier = Modifier.testTag("mic_permission_dialog_dismiss")
                ) {
                    Text("Use Text Input")
                }
            }
        )
    }
}
