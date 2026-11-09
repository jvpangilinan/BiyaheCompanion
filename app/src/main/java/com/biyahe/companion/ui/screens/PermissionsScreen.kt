package com.biyahe.companion.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.biyahe.companion.permissions.AppPermission
import com.biyahe.companion.ui.theme.BiyaheGreen
import com.biyahe.companion.ui.theme.BiyaheNavy
import com.biyahe.companion.ui.theme.BiyaheYellow
import com.biyahe.companion.util.findActivity
import androidx.compose.ui.unit.sp
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard

/** Shown once after unlocking if required permissions are still missing. */
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var refresh by remember { mutableIntStateOf(0) }
    // Remembers which permissions we already asked for (to detect "don't ask again").
    var askedCsv by rememberSaveable { mutableStateOf("") }
    val asked = remember(askedCsv) { askedCsv.split(",").filter { it.isNotEmpty() }.toSet() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedCsv = (asked + result.keys).joinToString(",")
        refresh++
    }

    // Re-check when returning from the system settings page.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val granted = remember(refresh) { AppPermission.entries.associateWith { it.isGranted(context) } }
    val requiredMissing = AppPermission.entries.any { it.required && granted[it] == false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        PosterHeading("Allow what Biyahe needs", fontSize = 28.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Biyahe uses your phone's sensors to measure the ride. You can change these anytime in Settings.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppPermission.entries.forEach { permission ->
                val isGranted = granted[permission] == true
                val permanentlyDenied = !isGranted && activity != null &&
                    permission.requestList.any { it in asked } &&
                    permission.requestList.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

                PermissionCard(
                    permission = permission,
                    granted = isGranted,
                    permanentlyDenied = permanentlyDenied,
                    onAllow = { launcher.launch(permission.requestList.toTypedArray()) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PosterButton(
            text = if (requiredMissing) "Continue anyway" else "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionCard(
    permission: AppPermission,
    granted: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            permission.icon,
            contentDescription = null,
            tint = PosterWhite,
            modifier = Modifier.size(44.dp).background(Ink).padding(10.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (if (permission.required) permission.title else "${permission.title} (optional)").uppercase(),
                style = PosterDisplay.copy(fontSize = 19.sp),
                color = Ink
            )
            Text(permission.reason, style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
        }
        Spacer(Modifier.width(10.dp))
        when {
            granted -> Icon(Icons.Filled.CheckCircle, contentDescription = "Allowed", tint = Ink)
            permanentlyDenied -> PosterButton("Settings", onOpenSettings, danger = true, compact = true)
            else -> PosterButton("Allow", onAllow, compact = true)
        }
    }
}
