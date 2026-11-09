package com.biyahe.companion.ui.admin

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.admin.AdminHazard
import com.biyahe.companion.admin.AdminSnapshot
import com.biyahe.companion.admin.AdminSource
import com.biyahe.companion.admin.Agency
import com.biyahe.companion.admin.DemoAdminSource
import com.biyahe.companion.admin.FirestoreAdminSource
import com.biyahe.companion.admin.ReportRecord
import com.biyahe.companion.admin.ReportText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AdminUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: AdminSnapshot = AdminSnapshot(),
    val message: String? = null
)

class AdminViewModel(private val container: AppContainer) : ViewModel() {

    private val source: AdminSource =
        if (container.isDemo) DemoAdminSource(container.tripRepository, container.hazardRepository) else FirestoreAdminSource()

    private val _ui = MutableStateFlow(AdminUiState())
    val ui: StateFlow<AdminUiState> = _ui.asStateFlow()

    val isDemo: Boolean get() = container.isDemo
    val adminName: String get() = container.accounts.account.value?.username ?: "admin"

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                if (!container.isDemo && container.accounts.ensureFirebaseSession() == null) {
                    error("No connection to the cloud. Check the internet and tap Try again.")
                }
                val data = withContext(Dispatchers.IO) { source.load() }
                _ui.value = AdminUiState(loading = false, data = data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = if (e.message?.contains("PERMISSION_DENIED") == true)
                        "The database refused: the Firestore rules for admin@biyahe.app are not set up yet (see notes/ADMIN_SETUP.md)."
                    else e.message ?: "Could not load the database."
                )
            }
        }
    }

    /** Opens the share sheet with the report (and photo), then records it as sent. */
    fun sendHazardReport(context: Context, h: AdminHazard, agency: Agency) {
        val now = System.currentTimeMillis()
        share(context, ReportText.hazardSubject(h), ReportText.hazard(h, agency, now), photoFile(context, h))
        record(ReportRecord(h.key, agency.short, now, adminName))
    }

    fun sendSafetySummary(context: Context, agency: Agency) {
        val now = System.currentTimeMillis()
        share(context, ReportText.safetySubject(), ReportText.safetySummary(_ui.value.data, agency, now), null)
        record(ReportRecord("safety-summary-$now", agency.short, now, adminName))
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

    /** Admin tool: five clearly-marked sample riders in Firestore (real mode only). */
    fun addSampleRiders(context: Context) = sampleJob("Adding sample riders...") {
        val (t, h) = com.biyahe.companion.admin.SampleRiders.add(context.applicationContext, container.cloudSync)
        "Added 5 sample riders with $t trips and $h hazard reports."
    }

    fun removeSampleRiders() = sampleJob("Removing sample riders...") {
        val n = com.biyahe.companion.admin.SampleRiders.remove()
        "Removed the sample riders ($n trips and reports)."
    }

    private fun sampleJob(busy: String, block: suspend () -> String) {
        _ui.value = _ui.value.copy(message = busy)
        viewModelScope.launch {
            val msg = try {
                if (container.accounts.ensureFirebaseSession() == null) error("No connection to the cloud.")
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message?.contains("PERMISSION_DENIED") == true)
                    "The Firestore rules don't allow sample riders yet: paste the updated rules from notes/ADMIN_SETUP.md."
                else "Couldn't do that: ${e.message}"
            }
            _ui.value = _ui.value.copy(message = msg)
            refresh()
        }
    }

    private fun record(r: ReportRecord) {
        viewModelScope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { source.recordReport(r) } }.isSuccess
            _ui.value = _ui.value.copy(
                data = _ui.value.data.copy(reports = _ui.value.data.reports + r),
                message = if (ok) "Marked as sent to ${r.agency}." else "Sent, but could not save the record (offline?)."
            )
        }
    }

    private fun share(context: Context, subject: String, body: String, photo: File?) {
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (photo != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.files", photo))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(send, "Send report with...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The hazard photo as a shareable file (cloud Base64 is written to the cache first). */
    private fun photoFile(context: Context, h: AdminHazard): File? = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "hazard_${h.key.replace('/', '_')}.jpg")
        when {
            h.photoBase64 != null -> out.writeBytes(Base64.decode(h.photoBase64, Base64.DEFAULT))
            h.photoPath != null && File(h.photoPath).exists() -> File(h.photoPath).copyTo(out, overwrite = true)
            else -> return@runCatching null
        }
        if (BitmapFactory.decodeFile(out.path) == null) null else out
    }.getOrNull()
}
