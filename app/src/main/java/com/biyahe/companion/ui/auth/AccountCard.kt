package com.biyahe.companion.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import com.biyahe.companion.ui.theme.PosterWhite
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.posterCard

/**
 * Small "@username  [log out icon]" button for the top-right corner of Home. Tapping it asks first;
 * logging out is blocked while a trip is recording.
 */
@Composable
fun AccountChip(recording: Boolean, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val account by container.accounts.account.collectAsStateWithLifecycle()
    val a = account ?: return
    var confirm by remember { mutableStateOf(false) }

    Row(
        modifier
            .background(Ink)
            .border(2.dp, PosterWhite, RectangleShape)
            .clickable { confirm = true }
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("@${a.username}", color = PosterWhite, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out", tint = PosterWhite, modifier = Modifier.size(18.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Log out of @${a.username}?") },
            text = {
                Text(
                    if (recording) "End the current trip first, then log out."
                    else "Your trips and reports stay saved to this account. Log in again any time to see them."
                )
            },
            confirmButton = {
                if (!recording) TextButton(onClick = { confirm = false; container.accounts.logOut() }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(if (recording) "OK" else "Cancel") } }
        )
    }
}

/** "Signed in as ..." with Log out, at the bottom of Home. Disabled while a trip is recording. */
@Composable
fun AccountCard(recording: Boolean, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val account by container.accounts.account.collectAsStateWithLifecycle()
    val a = account ?: return
    Row(
        modifier.padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.name.uppercase(), style = PosterDisplay.copy(fontSize = 18.sp), color = Ink)
            Text(if (recording) "End the trip before logging out." else "@${a.username} · your trips and reports are saved to this account",
                style = MaterialTheme.typography.bodySmall, color = PosterGrey)
        }
        PosterButton("Log out", { container.accounts.logOut() }, compact = true, enabled = !recording)
    }
}
