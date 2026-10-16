package com.byteclub.meetyourfriend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.byteclub.meetyourfriend.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    apiKeySet: Boolean,
    onDismiss: () -> Unit,
    onSettingsChanged: ((AppSettings) -> AppSettings) -> Unit,
    onSaveKey: (String) -> Unit,
    onTestKey: ((String) -> Unit) -> Unit,
    onLocationToggle: (Boolean) -> Unit,
) {
    var keyInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var myName by remember { mutableStateOf(settings.myName) }
    var whatsappTemplate by remember { mutableStateOf(settings.whatsappTemplate) }
    var defaultCountryCode by remember { mutableStateOf(settings.defaultCountryCode) }
    var extractionModel by remember { mutableStateOf(settings.extractionModel) }
    var briefModel by remember { mutableStateOf(settings.briefModel) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = myName,
                onValueChange = { value ->
                    myName = value
                    onSettingsChanged { it.copy(myName = value) }
                },
                label = { Text("My name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = whatsappTemplate,
                onValueChange = { value ->
                    whatsappTemplate = value
                    onSettingsChanged { it.copy(whatsappTemplate = value) }
                },
                label = { Text("WhatsApp message template") },
                supportingText = { Text("Placeholders: {name}, {organization}, {role}, {myName}") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = defaultCountryCode,
                onValueChange = { value ->
                    val countryCode = value.uppercase().take(2)
                    defaultCountryCode = countryCode
                    onSettingsChanged { it.copy(defaultCountryCode = countryCode) }
                },
                label = { Text("Default country code (ISO, e.g. AE)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            HorizontalDivider()
            Text("Gemini API key", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(if (apiKeySet) "Key saved securely · ••••••••" else "No key saved")
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = keyInput.isNotBlank(),
                    onClick = {
                        onSaveKey(keyInput)
                        keyInput = ""
                        testResult = "Key saved securely"
                    },
                ) { Text("Save key") }
                Button(
                    enabled = apiKeySet || keyInput.isNotBlank(),
                    onClick = {
                        if (keyInput.isNotBlank()) onSaveKey(keyInput)
                        keyInput = ""
                        onTestKey { testResult = it }
                    },
                ) { Text("Test key") }
            }
            testResult?.let { Text(it) }
            OutlinedTextField(
                value = extractionModel,
                onValueChange = { value ->
                    extractionModel = value
                    onSettingsChanged { it.copy(extractionModel = value) }
                },
                label = { Text("Extraction model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = briefModel,
                onValueChange = { value ->
                    briefModel = value
                    onSettingsChanged { it.copy(briefModel = value) }
                },
                label = { Text("Brief model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SettingSwitch(
                label = "Auto-generate brief",
                checked = settings.autoBrief,
                onCheckedChange = { checked -> onSettingsChanged { it.copy(autoBrief = checked) } },
            )
            SettingSwitch(
                label = "Location tagging (optional)",
                checked = settings.locationTagging,
                onCheckedChange = onLocationToggle,
            )
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
