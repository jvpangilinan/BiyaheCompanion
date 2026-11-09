package com.biyahe.companion.ui.auth

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
import androidx.compose.foundation.layout.width
import com.biyahe.companion.util.findActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.auth.Account
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHero
import com.biyahe.companion.ui.components.PosterLogo
import com.biyahe.companion.ui.components.PosterTagline
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.Paper
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.tour.LocalGuideTour
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

/**
 * Log in with a username and password. user / user123 opens the rider app, admin / admin123 the
 * admin dashboard, and anyone can create their own account (its trips and reports are saved to it).
 */
@Composable
fun LoginScreen(onBack: () -> Unit, onLoggedIn: (Account) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as BiyaheApp).container
    val accounts = container.accounts
    val scope = rememberCoroutineScope()

    var signUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Chanelly types the log-in for the audience, one letter at a time.
    val tour = LocalGuideTour.current
    val fill = tour?.fillRequest
    LaunchedEffect(fill) {
        val (u, p) = fill ?: return@LaunchedEffect
        signUp = false; error = null; username = ""; password = ""
        for (c in u) { username += c; kotlinx.coroutines.delay(90) }
        for (c in p) { password += c; kotlinx.coroutines.delay(70) }
        tour.fillRequest = null
    }

    fun go(u: String = username, p: String = password) {
        error = null; busy = true
        scope.launch {
            val r = if (signUp) accounts.signUp(u, name, p) else accounts.logIn(u, p)
            busy = false
            r.onSuccess(onLoggedIn).onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        PosterHero(height = 200.dp) {
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 24.dp, vertical = 16.dp)) {
                PosterLogo(big = 50.sp, small = 24.sp)
                Spacer(Modifier.height(10.dp))
                PosterTagline(fontSize = 14.sp)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (container.isDemo) "DEMO MODE · sample data, works offline" else "LOG IN",
                    color = if (container.isDemo) PosterRed else Ink, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ START OVER", style = MaterialTheme.typography.labelLarge) }
            }
            if (!container.isDemo) {
                GoogleButton(enabled = !busy) {
                    val activity = context.findActivity() ?: return@GoogleButton
                    error = null; busy = true
                    scope.launch {
                        val r = accounts.googleSignIn(activity)
                        busy = false
                        r.onSuccess(onLoggedIn).onFailure { e -> error = e.message?.takeIf { it.isNotBlank() } }
                    }
                }
                if (!accounts.googleReady) {
                    Text("Google sign-in will work once it's set up in Firebase (see notes/ADMIN_SETUP.md).",
                        style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                }
                Text("or with a username", style = MaterialTheme.typography.labelLarge, color = PosterGrey,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.fillMaxWidth()) {
                ModeTab("Log in", !signUp, Modifier.weight(1f)) { signUp = false; error = null; confirm = "" }
                ModeTab("Create account", signUp, Modifier.weight(1f)) { signUp = true; error = null; confirm = "" }
            }
            // Username first (what you log in with), then the display name, then the password.
            OutlinedTextField(
                username, { username = it.trim() },
                label = { Text(if (signUp) "Username (to log in, no spaces)" else "Username") },
                placeholder = { Text("e.g. juan.dc") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            if (signUp) {
                OutlinedTextField(name, { name = it }, label = { Text("Your name (shown in the app)") },
                    placeholder = { Text("e.g. Juan Dela Cruz") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            var showPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = { showPassword = !showPassword }) {
                        androidx.compose.material3.Icon(
                            if (showPassword) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (signUp) ImeAction.Next else ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            if (signUp) {
                // Type it twice so a typo can't lock you out (the eye button shows both).
                OutlinedTextField(
                    confirm, { confirm = it }, label = { Text("Confirm password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && confirm != password,
                    supportingText = {
                        when {
                            confirm.isEmpty() -> Unit
                            confirm != password -> Text("The passwords don't match.", color = PosterRed)
                            else -> Text("Passwords match ✓")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            error?.let { Text(it, color = PosterRed, style = MaterialTheme.typography.bodyMedium) }

            PosterButton(
                when { busy -> "Please wait..."; signUp -> "Create account"; else -> "Log in" },
                { go() }, Modifier.fillMaxWidth().tourTarget(TourTargets.LOGIN_BUTTON),
                enabled = !busy && username.length >= 3 && password.isNotEmpty() &&
                    (!signUp || (password.length >= 6 && confirm == password))
            )

            if (signUp) {
                Text(
                    "Your trips and hazard reports are saved to your account. Log in on any phone and they come back. " +
                        "Usernames: 3 to 20 letters or numbers. Passwords: at least 6 characters.",
                    style = MaterialTheme.typography.bodySmall, color = PosterGrey
                )
            } else if (container.isDemo) {
                // Demo mode only: the two sample accounts (offline, sample data; Chanelly types them on stage).
                Column(
                    Modifier.padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
                        .fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DEMO ACCOUNTS", style = PosterDisplay.copy(fontSize = 16.sp, letterSpacing = 1.5.sp), color = Ink)
                    QuickLogin("Rider app", "user", "user123", Modifier.fillMaxWidth()) { u, p -> username = u; password = p; go(u, p) }
                    QuickLogin("Admin dashboard", "admin", "admin123", Modifier.fillMaxWidth()) { u, p -> username = u; password = p; go(u, p) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickLogin(label: String, u: String, p: String, modifier: Modifier, onUse: (String, String) -> Unit) {
    Row(
        modifier.border(2.dp, Ink, RectangleShape).clickable { onUse(u, p) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = PosterDisplay.copy(fontSize = 16.sp), color = Ink)
            Text("$u  /  $p", style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
        }
        Text("TAP TO LOG IN ›", style = MaterialTheme.typography.labelMedium, color = PosterRed)
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) Ink else PosterWhite).border(2.dp, Ink, RectangleShape)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text.uppercase(), color = if (selected) PosterWhite else Ink, style = PosterDisplay.copy(fontSize = 17.sp)) }
}

/** White "Continue with Google" button with a simple coloured G. */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PosterWhite).border(2.dp, Ink, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("G", color = androidx.compose.ui.graphics.Color(0xFF4285F4), style = PosterDisplay.copy(fontSize = 22.sp))
        Spacer(Modifier.width(10.dp))
        Text("CONTINUE WITH GOOGLE", color = Ink, style = PosterDisplay.copy(fontSize = 18.sp))
    }
}
