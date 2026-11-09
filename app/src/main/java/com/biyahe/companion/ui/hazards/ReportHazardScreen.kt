package com.biyahe.companion.ui.hazards

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.biyahe.companion.data.local.entity.Hazard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.data.model.HazardType
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.components.BiyaheGold
import com.biyahe.companion.ui.theme.BiyaheNavy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.FilterChipDefaults
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterWhite
import androidx.compose.ui.unit.sp
import com.biyahe.companion.ui.util.formatDate
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private enum class LocStatus { IDLE, LOOKING, FOUND, FAILED }

/**
 * Report flow in one screen:
 *   1. live camera, tap the round button to take the photo
 *   2. pick the hazard type, add an optional note, see the GPS tag, Save
 * The report goes to SQLite first, then CloudSync uploads it.
 *
 * The camera runs inside the app (CameraX), so the app never goes to the background and the
 * fingerprint lock is not triggered by taking a photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHazardScreen(
    onClose: () -> Unit,
    /** Set to edit an existing report (change type/note, retake the photo, update the location). */
    editId: Long? = null,
    viewModel: HazardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var takenAt by rememberSaveable { mutableStateOf(0L) }
    var typeName by rememberSaveable { mutableStateOf(HazardType.POTHOLE.name) }
    var note by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    var locStatus by rememberSaveable { mutableStateOf(LocStatus.IDLE) }
    var saving by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // ---- Edit mode: load the report once and start on the form ----
    var original by remember { mutableStateOf<Hazard?>(null) }
    var loaded by rememberSaveable { mutableStateOf(editId == null) }
    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        val h = viewModel.load(editId) ?: run { onClose(); return@LaunchedEffect }
        original = h
        if (!loaded) {
            photoPath = h.photoPath          // "" = no photo yet; still opens the form
            typeName = h.type
            note = h.note
            lat = h.latitude
            lon = h.longitude
            takenAt = h.timestamp
            locStatus = LocStatus.FOUND
            loaded = true
        }
    }
    val editing = editId != null

    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    fun findLocation() {
        locStatus = LocStatus.LOOKING
        scope.launch {
            val result = HazardLocation.current(context)
            if (result != null) {
                lat = result.first
                lon = result.second
                locStatus = LocStatus.FOUND
            } else {
                locStatus = LocStatus.FAILED
            }
        }
    }

    // After a screen rotation the lookup coroutine is gone; start it again.
    LaunchedEffect(Unit) { if (locStatus == LocStatus.LOOKING) findLocation() }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { findLocation() }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }

    /** True when the current photo is the report's saved one (never delete that file here). */
    fun isSavedPhoto() = editing && photoPath == original?.photoPath

    fun discardPhoto() {
        if (!isSavedPhoto()) photoPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        photoPath = null
        if (!editing) {  // editing keeps the report's location; a new report finds it again
            lat = null
            lon = null
            locStatus = LocStatus.IDLE
        }
    }

    // Back from the form: a new report returns to the camera; editing leaves without saving.
    BackHandler(enabled = photoPath != null && !saving) {
        if (editing) {
            if (!isSavedPhoto()) photoPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
            onClose()
        } else discardPhoto()
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading report...") }
        return
    }

    val path = photoPath
    if (path == null) {
        // ---------------- Step 1: camera ----------------
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (cameraGranted) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onReady = { capture = it },
                    onError = { cameraError = it }
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("The camera permission is needed to photograph a hazard.", color = Color.White)
                    PosterButton("Allow camera", { cameraLauncher.launch(Manifest.permission.CAMERA) })
                }
            }

            TextButton(
                onClick = { if (editing) photoPath = original?.photoPath ?: "" else onClose() },  // editing: back to the form
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
            ) {
                Text(if (editing) "Keep the old photo" else "Cancel", color = Color.White)
            }

            cameraError?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 16.dp, end = 16.dp))
            }

            if (cameraGranted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = capture != null && !capturing) {
                            val imageCapture = capture ?: return@clickable
                            capturing = true
                            val dir = File(context.filesDir, "hazards").apply { mkdirs() }
                            val file = File(dir, "hazard_${System.currentTimeMillis()}.jpg")
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        capturing = false
                                        photoPath = file.absolutePath
                                        if (!editing) {  // a retake keeps the report's time and place
                                            takenAt = System.currentTimeMillis()
                                            if (HazardLocation.hasPermission(context)) findLocation()
                                            else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        capturing = false
                                        cameraError = "Could not take the photo. Try again."
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(62.dp).clip(CircleShape).background(if (capturing) Color.Gray else BiyaheGold))
                }
            }
        }
    } else {
        // ---------------- Step 2: details ----------------
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PosterHeading(if (editing) "Edit report" else "Report a hazard", fontSize = 28.sp)

            HazardPhoto(
                path = path,
                modifier = Modifier.fillMaxWidth().height(240.dp).border(2.dp, Ink, RectangleShape),
                maxSidePx = 1000
            )

            Text("What is it?", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HazardType.entries.forEach { type ->
                    FilterChip(
                        selected = typeName == type.name,
                        onClick = { typeName = type.name },
                        label = { Text(type.label) },
                        shape = RectangleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = PosterWhite)
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                maxLines = 4
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text((if (editing) "Reported " else "") + formatDate(takenAt), style = MaterialTheme.typography.bodyMedium)
                when (locStatus) {
                    LocStatus.FOUND -> Text(
                        "Location: " + String.format(Locale.US, "%.5f, %.5f", lat ?: 0.0, lon ?: 0.0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LocStatus.FAILED -> {
                        Text("Could not find your location. Check that GPS is on and location is allowed.", style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = {
                                if (HazardLocation.hasPermission(context)) findLocation()
                                else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }) { Text("Try again") }
                            TextButton(onClick = {
                                lat = 0.0
                                lon = 0.0
                                locStatus = LocStatus.FOUND
                            }) { Text("Save without location") }
                        }
                    }
                    else -> Text("Finding your location...", style = MaterialTheme.typography.bodyMedium)
                }
                if (editing && locStatus == LocStatus.FOUND) {
                    TextButton(onClick = {
                        if (HazardLocation.hasPermission(context)) findLocation()
                        else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) { Text("UPDATE LOCATION TO WHERE I AM") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterButton(if (editing) "Retake photo" else "Retake", { discardPhoto() }, Modifier.weight(1f), enabled = !saving)
                PosterButton(
                    text = if (saving) "Saving..." else if (editing) "Save changes" else "Save report",
                    onClick = {
                        saving = true
                        val o = original
                        if (editing && o != null) {
                            viewModel.updateReport(
                                original = o,
                                type = HazardType.fromName(typeName),
                                note = note,
                                photoPath = path,
                                latitude = lat ?: o.latitude,
                                longitude = lon ?: o.longitude,
                                onSaved = onClose
                            )
                            return@PosterButton
                        }
                        viewModel.saveReport(
                            type = HazardType.fromName(typeName),
                            note = note,
                            photoPath = path,
                            latitude = lat ?: 0.0,
                            longitude = lon ?: 0.0,
                            takenAtMs = takenAt,
                            onSaved = onClose
                        )
                    },
                    enabled = locStatus == LocStatus.FOUND && !saving,
                    modifier = Modifier.weight(1f),
                    danger = true
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    onReady: (ImageCapture) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    onReady(imageCapture)
                } catch (e: Exception) {
                    onError("Camera is not available on this device.")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/** The activity behind a Compose context (works whatever lifecycle-compose version you use). */
private fun Context.findLifecycleOwner(): LifecycleOwner {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    error("No LifecycleOwner found for the camera")
}
