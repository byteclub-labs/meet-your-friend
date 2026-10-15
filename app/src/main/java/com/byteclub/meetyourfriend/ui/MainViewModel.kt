package com.byteclub.meetyourfriend.ui

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import com.byteclub.meetyourfriend.brief.BriefWorker
import com.byteclub.meetyourfriend.brief.GeminiClient
import com.byteclub.meetyourfriend.brief.GeminiKeyStore
import com.byteclub.meetyourfriend.data.Connect
import com.byteclub.meetyourfriend.data.ConnectDao
import com.byteclub.meetyourfriend.data.SettingsRepository
import com.byteclub.meetyourfriend.share.CsvExporter
import com.byteclub.meetyourfriend.voice.FieldExtractor
import com.byteclub.meetyourfriend.voice.PhoneNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class DuplicatePrompt(val existing: Connect, val pending: Connect)

data class MeetUiState(
    val connects: List<Connect> = emptyList(),
    val allConnects: List<Connect> = emptyList(),
    val query: String = "",
    val selectedId: Long? = null,
    val name: String = "",
    val organization: String = "",
    val role: String = "",
    val mobile: String = "",
    val linkedinUrl: String? = null,
    val mobileError: Boolean = false,
    val selfiePath: String? = null,
    val transcript: String = "",
    val partialTranscript: String = "",
    val status: String = "Tap to speak · double-tap for a selfie",
    val listening: Boolean = false,
    val settings: com.byteclub.meetyourfriend.data.AppSettings =
        com.byteclub.meetyourfriend.data.AppSettings(),
    val apiKeySet: Boolean = false,
    val privacyAccepted: Boolean = true,
    val duplicate: DuplicatePrompt? = null,
    val snackbar: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dao: ConnectDao,
    private val settingsRepository: SettingsRepository,
    private val keyStore: GeminiKeyStore,
    private val gemini: GeminiClient,
    private val extractor: FieldExtractor,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(MeetUiState(apiKeySet = keyStore.hasKey()))
    val state: StateFlow<MeetUiState> = _state.asStateFlow()
    private var saveJob: Job? = null
    private var mobileValidationJob: Job? = null

    init {
        viewModelScope.launch {
            dao.observeAll().collect { all ->
                val query = _state.value.query
                _state.value = _state.value.copy(
                    allConnects = all,
                    connects = filterConnects(all, query),
                )
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(
                    settings = settings,
                    privacyAccepted = settings.privacyNoticeAccepted,
                )
            }
        }
    }

    fun updateField(field: FormField, value: String) {
        val updated = when (field) {
            FormField.NAME -> _state.value.copy(name = value)
            FormField.ORGANIZATION -> _state.value.copy(organization = value)
            FormField.ROLE -> _state.value.copy(role = value)
            FormField.MOBILE -> _state.value.copy(mobile = value, mobileError = false)
        }
        _state.value = updated
        if (field == FormField.MOBILE) validateMobile(value)
        scheduleSave()
    }

    fun setQuery(value: String) {
        val all = _state.value.allConnects
        _state.value = _state.value.copy(query = value, connects = filterConnects(all, value))
    }

    fun setListening(listening: Boolean) {
        _state.value = _state.value.copy(
            listening = listening,
            status = if (listening) "Listening…" else _state.value.status,
        )
    }

    fun setPartialTranscript(value: String) {
        _state.value = _state.value.copy(partialTranscript = value)
    }

    fun setStatus(value: String) {
        _state.value = _state.value.copy(status = value)
    }

    fun processTranscript(transcript: String) {
        viewModelScope.launch {
            val extracted = extractor.extract(transcript)
            val current = _state.value
            val firstCapture = current.transcript.isBlank()
            val region = settingsRepository.resolvedRegion()
            val normalizedMobile = extracted.mobile?.let { PhoneNormalizer(region).normalize(it) }
            val mobile = normalizedMobile ?: extracted.mobile
            fun merge(old: String, new: String?): String =
                if (new != null && (firstCapture || old.isBlank())) new else old
            _state.value = current.copy(
                name = merge(current.name, extracted.name),
                organization = merge(current.organization, extracted.organization),
                role = merge(current.role, extracted.role),
                mobile = merge(current.mobile, mobile),
                mobileError = extracted.mobile != null && normalizedMobile == null,
                transcript = listOf(current.transcript, transcript).filter(String::isNotBlank)
                    .joinToString("\n"),
                partialTranscript = "",
                status = "",
            )
            _state.value = _state.value.copy(status = missingStatus())
            scheduleSave()
        }
    }

    fun load(connect: Connect) {
        _state.value = _state.value.copy(
            selectedId = connect.id,
            name = connect.name,
            organization = connect.organization,
            role = connect.role,
            mobile = connect.mobileE164,
            linkedinUrl = connect.linkedinUrl,
            mobileError = false,
            selfiePath = connect.selfiePath,
            transcript = connect.transcript,
            partialTranscript = "",
            status = "Loaded ${connect.name}",
        )
    }

    fun newConnect() {
        saveJob?.cancel()
        _state.value = _state.value.copy(
            selectedId = null,
            name = "",
            organization = "",
            role = "",
            mobile = "",
            linkedinUrl = null,
            mobileError = false,
            selfiePath = null,
            transcript = "",
            partialTranscript = "",
            status = "New connect",
            duplicate = null,
        )
    }

    fun setSelfie(path: String) {
        _state.value = _state.value.copy(selfiePath = path, status = "Selfie saved")
        scheduleSave()
    }

    fun keepDuplicate() {
        val pending = _state.value.duplicate?.pending ?: return
        viewModelScope.launch {
            val id = dao.upsert(pending.copy(id = 0))
            _state.value = _state.value.copy(selectedId = id, duplicate = null, status = "Connect saved")
            maybeGenerateBrief(id)
        }
    }

    fun openDuplicate() {
        _state.value.duplicate?.existing?.let(::load)
        _state.value = _state.value.copy(duplicate = null)
    }

    fun acceptPrivacyNotice() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(privacyNoticeAccepted = true) }
        }
    }

    fun updateSettings(transform: (com.byteclub.meetyourfriend.data.AppSettings) ->
        com.byteclub.meetyourfriend.data.AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun saveGeminiKey(key: String) {
        keyStore.setKey(key)
        _state.value = _state.value.copy(apiKeySet = keyStore.hasKey())
    }

    fun testGeminiKey(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val key = keyStore.getKey()
            if (key == null) {
                onResult("Add an API key first")
                return@launch
            }
            runCatching {
                gemini.testKey(key, _state.value.settings.extractionModel)
            }.onSuccess { onResult("OK") }
                .onFailure { onResult("Error: ${it.message ?: "request failed"}") }
        }
    }

    fun generateBrief() {
        val id = _state.value.selectedId
        if (id == null) {
            _state.value = _state.value.copy(snackbar = "Save a name and mobile first")
        } else if (!keyStore.hasKey()) {
            _state.value = _state.value.copy(snackbar = "Add your Gemini API key in Settings")
        } else {
            BriefWorker.enqueue(context, id)
            _state.value = _state.value.copy(snackbar = "Brief generation queued")
        }
    }

    fun markWhatsAppSent() {
        val id = _state.value.selectedId ?: return
        viewModelScope.launch {
            dao.getById(id)?.let { dao.upsert(it.copy(whatsappSentAt = java.time.Instant.now())) }
        }
    }

    fun delete(connect: Connect) {
        viewModelScope.launch {
            dao.deleteById(connect.id)
            connect.selfiePath?.let { java.io.File(it).delete() }
            connect.voiceNotePath?.let { java.io.File(it).delete() }
            if (_state.value.selectedId == connect.id) newConnect()
            _state.value = _state.value.copy(snackbar = "Connect deleted")
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            CsvExporter.writeAndShare(context, dao.observeAll().first())
        }
    }

    fun saveToContacts() {
        val current = _state.value
        if (current.name.isBlank() || current.mobile.isBlank()) {
            _state.value = current.copy(snackbar = "Add a name and mobile first")
            return
        }
        val intent = android.content.Intent(android.provider.ContactsContract.Intents.Insert.ACTION).apply {
            type = android.provider.ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(android.provider.ContactsContract.Intents.Insert.NAME, current.name)
            putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, current.mobile)
            putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, current.organization)
            putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, current.role)
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun handleSharedText(text: String) {
        val profile = Regex("https?://(?:www\\.)?linkedin\\.com/in/[^\\s<>]+", RegexOption.IGNORE_CASE)
            .find(text)?.value?.trimEnd('.', ',', ')', ']') ?: return
        viewModelScope.launch {
            val current = _state.value
            val selected = current.selectedId?.let { dao.getById(it) }
            if (selected != null) {
                dao.upsert(selected.copy(linkedinUrl = profile))
                if (keyStore.hasKey()) BriefWorker.enqueue(context, selected.id)
                _state.value = _state.value.copy(linkedinUrl = profile, snackbar = "LinkedIn profile linked")
            } else if (current.name.isNotBlank() || current.mobile.isNotBlank()) {
                _state.value = _state.value.copy(linkedinUrl = profile, snackbar = "LinkedIn profile linked")
                scheduleSave()
            } else {
                val target = dao.observeAll().first().firstOrNull()
                if (target != null) {
                    dao.upsert(target.copy(linkedinUrl = profile))
                    if (keyStore.hasKey()) BriefWorker.enqueue(context, target.id)
                    _state.value = _state.value.copy(snackbar = "LinkedIn profile linked")
                }
            }
        }
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbar = null)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val snapshot = _state.value
        if (snapshot.name.isBlank() || snapshot.mobile.isBlank()) return
        saveJob = viewModelScope.launch {
            delay(500)
            save(snapshot)
        }
    }

    private fun validateMobile(value: String) {
        mobileValidationJob?.cancel()
        if (value.isBlank()) return
        mobileValidationJob = viewModelScope.launch {
            delay(350)
            val region = settingsRepository.resolvedRegion()
            val valid = PhoneNormalizer(region).normalize(value) != null
            if (_state.value.mobile == value) {
                _state.value = _state.value.copy(mobileError = !valid)
            }
        }
    }

    private suspend fun save(snapshot: MeetUiState) {
        val region = settingsRepository.resolvedRegion()
        val mobile = PhoneNormalizer(region).normalize(snapshot.mobile)
        if (mobile == null) {
            _state.value = _state.value.copy(mobileError = true, status = "Not a valid number")
            return
        }
        val existingId = snapshot.selectedId
        if (existingId == null) {
            val duplicate = dao.findByMobile(mobile)
            if (duplicate != null) {
                val pending = buildConnect(snapshot, mobile, 0)
                _state.value = _state.value.copy(
                    duplicate = DuplicatePrompt(duplicate, pending),
                    status = "This number is already saved",
                )
                return
            }
        }
        val existing = existingId?.let { dao.getById(it) }
        val connect = buildConnect(snapshot, mobile, existingId ?: 0).copy(
            metAt = existing?.metAt ?: java.time.Instant.now(),
            whatsappSentAt = existing?.whatsappSentAt,
            briefText = existing?.briefText,
            briefSources = existing?.briefSources,
            briefConfident = existing?.briefConfident,
            voiceNotePath = existing?.voiceNotePath,
            linkedinUrl = snapshot.linkedinUrl ?: existing?.linkedinUrl,
            location = existing?.location ?: if (settingsRepository.settings.first().locationTagging) {
                lastKnownLocation()
            } else {
                null
            },
        )
        val id = dao.upsert(connect)
        _state.value = _state.value.copy(selectedId = id, mobile = mobile, mobileError = false)
        if (existing == null) maybeGenerateBrief(id)
    }

    private fun buildConnect(snapshot: MeetUiState, mobile: String, id: Long) = Connect(
        id = id,
        name = snapshot.name.trim(),
        organization = snapshot.organization.trim(),
        role = snapshot.role.trim(),
        mobileE164 = mobile,
        selfiePath = snapshot.selfiePath,
        transcript = snapshot.transcript,
        linkedinUrl = snapshot.linkedinUrl,
    )

    private fun maybeGenerateBrief(id: Long) {
        if (_state.value.settings.autoBrief && keyStore.hasKey()) {
            BriefWorker.enqueue(context, id)
        }
    }

    private fun missingStatus(): String {
        val current = _state.value
        val missing = buildList {
            if (current.name.isBlank()) add("Name")
            if (current.organization.isBlank()) add("Organization")
            if (current.role.isBlank()) add("Role")
            if (current.mobile.isBlank()) add("Mobile")
        }
        return if (missing.isEmpty()) "All details captured" else "Missing: ${missing.joinToString(", ")} — tap and say it"
    }

    private fun filterConnects(connects: List<Connect>, query: String): List<Connect> =
        if (query.isBlank()) connects else connects.filter {
            it.name.contains(query, true) || it.organization.contains(query, true)
        }

    private fun lastKnownLocation(): String? = runCatching {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            ?: return null
        @Suppress("DEPRECATION")
        Geocoder(context, java.util.Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()?.locality
    }.getOrNull()
}

enum class FormField { NAME, ORGANIZATION, ROLE, MOBILE }
