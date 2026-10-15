package com.byteclub.meetyourfriend.ui

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.byteclub.meetyourfriend.brief.BriefSource
import com.byteclub.meetyourfriend.brief.GeminiClient
import com.byteclub.meetyourfriend.data.Connect
import com.byteclub.meetyourfriend.share.LinkedInLauncher
import com.byteclub.meetyourfriend.share.WhatsAppLauncher
import com.byteclub.meetyourfriend.voice.SpeechCapture
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetScreen(
    viewModel: MainViewModel,
    sharedText: String?,
    onSharedHandled: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var settingsVisible by remember { mutableStateOf(false) }
    var cameraVisible by remember { mutableStateOf(false) }
    val fallbackVisible = remember { mutableStateOf(false) }
    val fallbackTranscript = remember { mutableStateOf("") }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val speechCapture = remember(context, viewModel) {
        SpeechCapture(context, object : SpeechCapture.Listener {
            override fun onListeningChanged(listening: Boolean) = viewModel.setListening(listening)
            override fun onPartial(text: String) = viewModel.setPartialTranscript(text)
            override fun onTranscript(text: String) = viewModel.processTranscript(text)
            override fun onFailure(message: String, showFallback: Boolean) {
                viewModel.setStatus(message)
                if (showFallback) fallbackVisible.value = true
            }
        })
    }
    DisposableEffect(speechCapture) {
        onDispose { speechCapture.destroy() }
    }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphoneGranted = granted
        if (granted) speechCapture.start() else viewModel.setStatus("Microphone permission is needed to speak")
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraVisible = true else viewModel.setStatus("Camera permission is needed for a selfie")
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.updateSettings { it.copy(locationTagging = granted) }
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.handleSharedText(sharedText)
            onSharedHandled()
        }
    }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbar.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inset ->
        Column(
            Modifier.fillMaxSize()
                .background(Color(0xFFF5F4EF))
                .verticalScroll(rememberScrollState())
                .padding(inset)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Meet Your Friend",
                    modifier = Modifier.weight(1f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = viewModel::newConnect) { Text("New") }
                TextButton(onClick = viewModel::exportCsv) { Text("Export CSV") }
                TextButton(onClick = { settingsVisible = true }) { Text("Settings") }
            }
            Spacer(Modifier.height(8.dp))
            MeetButton(
                onTap = {
                    if (state.listening) {
                        speechCapture.stop()
                    } else if (microphoneGranted) {
                        speechCapture.start()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onDoubleTap = {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                },
                onLongPress = viewModel::newConnect,
                listening = state.listening,
            )
            Text(state.status, Modifier.padding(top = 8.dp), color = Color(0xFF59635F))
            if (state.partialTranscript.isNotBlank()) {
                Text(state.partialTranscript, Modifier.padding(top = 4.dp), color = Color(0xFF175B52))
            }

            if (state.selfiePath != null) {
                AsyncImage(
                    model = File(state.selfiePath!!),
                    contentDescription = "Selfie",
                    modifier = Modifier.padding(top = 12.dp).size(88.dp).background(Color.LightGray, CircleShape),
                )
            }

            Spacer(Modifier.height(12.dp))
            FormInput("Name", state.name, { viewModel.updateField(FormField.NAME, it) })
            FormInput(
                "Organization",
                state.organization,
                { viewModel.updateField(FormField.ORGANIZATION, it) },
            )
            FormInput("Role", state.role, { viewModel.updateField(FormField.ROLE, it) })
            FormInput(
                "Mobile",
                state.mobile,
                { viewModel.updateField(FormField.MOBILE, it) },
                isError = state.mobileError,
            )
            if (state.mobileError) {
                Text("Not a valid number", color = Color(0xFFB3261E), modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            LinkedInLauncher.open(context, state.name, state.organization)
                        }.onFailure { viewModel.setStatus("Could not open LinkedIn search") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Find on LinkedIn") }
                OutlinedButton(
                    onClick = {
                        val message = state.settings.whatsappTemplate
                            .replace("{name}", state.name)
                            .replace("{organization}", state.organization)
                            .replace("{role}", state.role)
                            .replace("{myName}", state.settings.myName)
                        runCatching { WhatsAppLauncher.launch(context, state.mobile, message) }
                            .onSuccess { viewModel.markWhatsAppSent() }
                            .onFailure { viewModel.setStatus("Could not open WhatsApp or SMS") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("WhatsApp ✉") }
            }
            OutlinedButton(onClick = viewModel::saveToContacts, modifier = Modifier.fillMaxWidth()) {
                Text("Save to Contacts")
            }

            val selectedConnect = state.selectedId?.let { id -> state.allConnects.firstOrNull { it.id == id } }
            BriefCard(
                connect = selectedConnect,
                apiKeySet = state.apiKeySet,
                onGenerate = viewModel::generateBrief,
                onPlay = { path ->
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer.create(context, Uri.fromFile(File(path)))
                    mediaPlayer?.start()
                },
                onStop = {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                },
                onShareVoice = { path ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(path),
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "audio/wav"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("My connects (${state.allConnects.size})", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search name or organization") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                singleLine = true,
            )
            ConnectList(
                connects = state.connects,
                onClick = viewModel::load,
                onDelete = viewModel::delete,
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (settingsVisible) {
        SettingsSheet(
            settings = state.settings,
            apiKeySet = state.apiKeySet,
            onDismiss = { settingsVisible = false },
            onSettingsChanged = viewModel::updateSettings,
            onSaveKey = viewModel::saveGeminiKey,
            onTestKey = viewModel::testGeminiKey,
            onLocationToggle = { enabled ->
                if (enabled) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                else viewModel.updateSettings { it.copy(locationTagging = false) }
            },
        )
    }
    if (cameraVisible) {
        SelfieCamera(
            onCaptured = { path ->
                viewModel.setSelfie(path)
                cameraVisible = false
            },
            onClose = { cameraVisible = false },
        )
    }
    if (fallbackVisible.value) {
        AlertDialog(
            onDismissRequest = { fallbackVisible.value = false },
            title = { Text("Type what you'd say") },
            text = {
                OutlinedTextField(
                    value = fallbackTranscript.value,
                    onValueChange = { fallbackTranscript.value = it },
                    label = { Text("Introduction") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fallbackTranscript.value.isNotBlank()) viewModel.processTranscript(fallbackTranscript.value)
                    fallbackTranscript.value = ""
                    fallbackVisible.value = false
                }) { Text("Use text") }
            },
            dismissButton = {
                TextButton(onClick = { fallbackVisible.value = false }) { Text("Cancel") }
            },
        )
    }
    state.duplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = { viewModel.openDuplicate() },
            title = { Text("You already met ${duplicate.existing.name}") },
            text = { Text("This mobile number is already saved.") },
            confirmButton = {
                TextButton(onClick = viewModel::openDuplicate) { Text("Open existing") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::keepDuplicate) { Text("Keep both") }
            },
        )
    }
    if (!state.privacyAccepted) {
        AlertDialog(
            onDismissRequest = viewModel::acceptPrivacyNotice,
            title = { Text("Your privacy") },
            text = {
                Text(
                    "Your connects stay on this device. If you use Gemini, the transcript (which may include a phone number) is sent to Google for extraction; brief requests send the name, organization, role, and any LinkedIn profile URL. Photos and phone numbers are not uploaded separately.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::acceptPrivacyNotice) { Text("Continue") }
            },
        )
    }
}

@Composable
private fun FormInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        isError = isError,
        singleLine = true,
    )
}

@Composable
private fun BriefCard(
    connect: Connect?,
    apiKeySet: Boolean,
    onGenerate: () -> Unit,
    onPlay: (String) -> Unit,
    onStop: () -> Unit,
    onShareVoice: (String) -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Brief", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (connect?.briefConfident == false) {
                    Text(
                        "Unverified",
                        Modifier.background(Color(0xFFFFE5D5), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color(0xFF8D3116),
                    )
                }
            }
            when {
                connect?.briefText?.isNotBlank() == true -> Text(connect.briefText)
                !apiKeySet -> Text("Add your Gemini API key in Settings")
                connect == null -> Text("A brief appears after this connect is saved.")
                else -> Text("No brief yet.")
            }
            connect?.briefSources?.let { sourcesJson ->
                parseSources(sourcesJson).forEach { source ->
                    Text(
                        source.title.ifBlank { source.uri },
                        color = Color(0xFF175B52),
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(source.uri)),
                                )
                            }
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (connect?.voiceNotePath != null) {
                    TextButtonWithIcon("▶ Play", Icons.Default.PlayArrow) { onPlay(connect.voiceNotePath) }
                    TextButtonWithIcon("Stop", Icons.Default.Stop, onStop)
                    TextButton(onClick = { onShareVoice(connect.voiceNotePath) }) { Text("Share audio") }
                }
                Button(onClick = onGenerate, enabled = connect != null && apiKeySet) {
                    Text(if (connect?.briefText.isNullOrBlank()) "Generate brief" else "Regenerate")
                }
            }
        }
    }
}

@Composable
private fun TextButtonWithIcon(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Text(label)
    }
}

private fun parseSources(value: String): List<BriefSource> = runCatching {
    Json.parseToJsonElement(value).jsonArray.mapNotNull { element ->
        val item = element.jsonObject
        val uri = item["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
        BriefSource(item["title"]?.jsonPrimitive?.content.orEmpty(), uri)
    }
}.getOrDefault(emptyList())
