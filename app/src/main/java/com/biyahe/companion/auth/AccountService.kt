package com.biyahe.companion.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Role { USER, ADMIN }

data class Account(
    /** Firebase uid once known; "local-<username>" before the first online log-in. */
    val uid: String,
    val username: String,
    val name: String,
    val role: Role,
    val demo: Boolean = false
)

/**
 * Username + password log-in.
 *
 * Built-in accounts (as the class asked for):
 *   admin / admin123  -> the admin dashboard
 *   user  / user123   -> the rider app
 * Anyone can also create their own account.
 *
 * Every account is a Firebase account behind the scenes ("<username>@biyahe.app"), so each rider's
 * uploads are saved under their own account and come back when they log in on any phone. The admin
 * dashboard can read everyone's data because the Firestore rules let admin@biyahe.app read all.
 *
 * The built-in accounts also work offline (checked on the phone), and any account that has logged in
 * on this phone before can log in again offline. Uploads catch up once there is a connection.
 *
 * Demo mode: everything stays on the phone, Firebase is never used.
 */
class AccountService(context: Context, private val isDemo: Boolean) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val prefix = prefix(isDemo)

    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    /** Kept in memory only (never saved): lets uploads re-open Firebase after an offline log-in. */
    @Volatile private var sessionPassword: String? = null

    private val firebaseReady: Boolean get() = !isDemo && FirebaseApp.getApps(appContext).isNotEmpty()

    // ---- Log in ---------------------------------------------------------------------------------------

    suspend fun logIn(rawUsername: String, password: String): Result<Account> {
        val u = normalize(rawUsername) ?: return fail("Usernames use 3 to 20 letters, numbers, dots or dashes.")
        val builtIn = if (firebaseReady) null else BUILT_IN[u]   // demo-only accounts
        if (builtIn != null && password != builtIn.password) return fail("Wrong username or password.")
        val role = if (u == ADMIN_USERNAME) Role.ADMIN else Role.USER

        if (!firebaseReady) {
            // Demo mode (or no Firebase): built-ins and accounts created on this phone.
            if (builtIn == null && !localPasswordMatches(u, password)) return fail("Wrong username or password.")
            return Result.success(save(Account(localUid(u), u, displayName(u), role, demo = isDemo), password))
        }

        return try {
            val user = withTimeout(15_000L) { firebaseSignIn(u, password, createIfBuiltIn = builtIn != null) }
            val acct = Account(user.uid, u, user.displayName ?: displayName(u), role)
            writeProfile(acct)
            Result.success(save(acct, password))
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) offlineLogIn(u, password, builtIn != null, role) else throw e
        } catch (e: FirebaseNetworkException) {
            offlineLogIn(u, password, builtIn != null, role)
        } catch (e: Exception) {
            fail(friendly(e))
        }
    }

    // ---- Create account ---------------------------------------------------------------------------------

    suspend fun signUp(rawUsername: String, name: String, password: String): Result<Account> {
        val u = normalize(rawUsername) ?: return fail("Usernames use 3 to 20 letters, numbers, dots or dashes.")
        if ((!firebaseReady && u in BUILT_IN) || u.startsWith("g.")) return fail("That username is taken.")
        if (password.length < 6) return fail("Use a password with at least 6 characters.")
        // The admin account can read every rider's data: it needs a strong password.
        if (u == ADMIN_USERNAME && (password.length < 10 || password.none { it.isDigit() } || password.none { it.isLetter() })) {
            return fail("The admin password needs at least 10 characters, with letters and numbers.")
        }
        val shown = name.trim().ifEmpty { displayName(u) }

        if (!firebaseReady) {
            if (prefs.contains(hashKey(u))) return fail("That username is taken on this phone.")
            prefs.edit().putString(nameKey(u), shown).apply()
            return Result.success(save(Account(localUid(u), u, shown, Role.USER, demo = isDemo), password))
        }
        return try {
            val user = withTimeout(15_000L) {
                val auth = FirebaseAuth.getInstance()
                val cred = EmailAuthProvider.getCredential(email(u), password)
                val current = auth.currentUser
                // A phone that synced anonymously keeps its uid (and cloud data) by upgrading that account.
                (if (current != null && current.isAnonymous && u != ADMIN_USERNAME) current.linkWithCredential(cred).await()
                else auth.createUserWithEmailAndPassword(email(u), password).await()).user ?: error("Sign-up failed")
            }
            prefs.edit().putString(nameKey(u), shown).apply()
            val acct = Account(user.uid, u, shown, if (u == ADMIN_USERNAME) Role.ADMIN else Role.USER)
            writeProfile(acct, created = true)
            Result.success(save(acct, password))
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) fail("Creating an account needs an internet connection.") else throw e
        } catch (e: FirebaseNetworkException) {
            fail("Creating an account needs an internet connection.")
        } catch (e: Exception) {
            fail(friendly(e))
        }
    }

    // ---- Continue with Google --------------------------------------------------------------------------

    /** False until Firebase's Google sign-in is set up (SHA-1 added, google-services.json downloaded again). */
    val googleReady: Boolean get() = firebaseReady && webClientId() != null

    /**
     * Shows Google's account picker, then signs in to Firebase with that Google account. A phone that
     * synced anonymously is upgraded (same uid, cloud data kept). Google accounts are riders; the
     * admin dashboard stays on admin / admin123.
     */
    suspend fun googleSignIn(activity: android.app.Activity): Result<Account> {
        val clientId = webClientId()
            ?: return fail("Google sign-in isn't set up yet: add the SHA-1 in Firebase and download google-services.json again.")
        return try {
            val request = androidx.credentials.GetCredentialRequest.Builder()
                .addCredentialOption(
                    com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(clientId).build()
                )
                .build()
            val result = androidx.credentials.CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred !is androidx.credentials.CustomCredential ||
                cred.type != com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) return fail("That isn't a Google account.")
            val google = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(cred.data)
            val firebaseCred = com.google.firebase.auth.GoogleAuthProvider.getCredential(google.idToken, null)

            val user = withTimeout(20_000L) {
                val auth = FirebaseAuth.getInstance()
                val current = auth.currentUser
                if (current != null && current.isAnonymous) {
                    try { current.linkWithCredential(firebaseCred).await().user }
                    catch (e: FirebaseAuthUserCollisionException) { auth.signInWithCredential(firebaseCred).await().user }
                } else auth.signInWithCredential(firebaseCred).await().user
            } ?: return fail("Google sign-in failed.")

            val email = user.email ?: google.id
            val username = googleUsername(email)
            val name = user.displayName ?: google.displayName ?: username
            prefs.edit().putString(nameKey(username), name).apply()
            val acct = Account(user.uid, username, name, Role.USER)
            writeProfile(acct, created = false, extra = mapOf("email" to email, "provider" to "google"))
            Result.success(save(acct, password = null))
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            fail("")  // the user closed the picker: no message
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            fail("No Google account on this phone. Add one in the phone's Settings → Accounts.")
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            fail(
                if (e.message?.contains("10") == true || e.message?.contains("developer", ignoreCase = true) == true)
                    "Google sign-in is blocked: the app's SHA-1 isn't registered in Firebase yet."
                else "Google sign-in failed: ${e.message ?: e.type}"
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) fail("No internet connection. Try again when you're online.") else throw e
        } catch (e: FirebaseNetworkException) {
            fail("No internet connection. Try again when you're online.")
        } catch (e: Exception) {
            fail(e.message ?: "Google sign-in failed.")
        }
    }

    /** "juan.dela.cruz@gmail.com" -> "g.juan.dela.cruz" (prefix keeps it apart from typed usernames). */
    private fun googleUsername(email: String): String {
        val local = email.substringBefore('@').lowercase().replace(Regex("[^a-z0-9._-]"), "")
        return ("g." + local).take(20).ifBlank { "g.rider" }
    }

    /** Firebase writes this resource from google-services.json once Google sign-in is configured. */
    private fun webClientId(): String? {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) null else appContext.getString(id).takeIf { it.isNotBlank() }
    }

    /** The two class-demo accounts keep their fixed passwords. */
    fun isBuiltIn(username: String) = !firebaseReady && username in BUILT_IN

    /** Signed in with Google (usernames "g.something" are only made by Google sign-in). */
    fun isGoogle(username: String) = username.startsWith("g.")

    /** New display name, on the phone and (when online) in the rider's cloud profile. */
    suspend fun rename(newName: String): Result<Unit> {
        val a = _account.value ?: return Result.failure(IllegalStateException("Not logged in."))
        val n = newName.trim().take(40)
        if (n.isEmpty()) return Result.failure(IllegalStateException("Type a name."))
        prefs.edit().putString(nameKey(a.username), n).apply()
        _account.value = a.copy(name = n)
        if (firebaseReady && !a.uid.startsWith("local-")) {
            runCatching {
                withTimeout(10_000L) {
                    FirebaseFirestore.getInstance().collection("users").document(a.uid)
                        .set(mapOf("name" to n), SetOptions.merge()).await()
                }
            }
        }
        return Result.success(Unit)
    }

    /** Checks the current password, then changes it (in Firebase too; that part needs internet). */
    suspend fun changePassword(current: String, new: String): Result<Unit> {
        val a = _account.value ?: return Result.failure(IllegalStateException("Not logged in."))
        if (isBuiltIn(a.username)) return Result.failure(IllegalStateException("The built-in demo accounts keep their passwords."))
        if (isGoogle(a.username)) return Result.failure(IllegalStateException("Google accounts use your Google password. Change it in your Google account."))
        if (new.length < 6) return Result.failure(IllegalStateException("Use a new password with at least 6 characters."))
        if (!localPasswordMatches(a.username, current)) return Result.failure(IllegalStateException("Your current password is wrong."))
        if (firebaseReady) {
            try {
                withTimeout(15_000L) {
                    ensureFirebaseSession()
                    val user = FirebaseAuth.getInstance().currentUser ?: error("Changing your password needs an internet connection.")
                    user.reauthenticate(EmailAuthProvider.getCredential(email(a.username), current)).await()
                    user.updatePassword(new).await()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    return Result.failure(IllegalStateException("Changing your password needs an internet connection."))
                }
                throw e
            } catch (e: FirebaseNetworkException) {
                return Result.failure(IllegalStateException("Changing your password needs an internet connection."))
            } catch (e: Exception) {
                return Result.failure(IllegalStateException(e.message ?: "Could not change the password."))
            }
        }
        prefs.edit().putString(hashKey(a.username), hash(a.username, new)).apply()
        sessionPassword = new
        return Result.success(Unit)
    }

    fun logOut() {
        if (firebaseReady) runCatching { FirebaseAuth.getInstance().signOut() }
        sessionPassword = null
        prefs.edit().remove(prefix + "username").remove(prefix + "uid").remove(prefix + "role").apply()
        _account.value = null
    }

    /**
     * For uploads: makes sure Firebase is signed in as the current account (after an offline log-in,
     * or if Firebase dropped its session). Returns the uid, or null when that isn't possible now.
     */
    suspend fun ensureFirebaseSession(): String? {
        val a = _account.value ?: return null
        if (!firebaseReady || a.demo) return null
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.takeIf { it.uid == a.uid || it.email == email(a.username) }?.let { return it.uid }
        val pw = sessionPassword ?: return null
        return runCatching {
            withTimeout(15_000L) { firebaseSignIn(a.username, pw, createIfBuiltIn = false) }
        }.getOrNull()?.also { user ->
            if (a.uid != user.uid) save(a.copy(uid = user.uid), pw)
        }?.uid
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private suspend fun firebaseSignIn(u: String, password: String, createIfBuiltIn: Boolean): FirebaseUser {
        val auth = FirebaseAuth.getInstance()
        return try {
            auth.signInWithEmailAndPassword(email(u), password).await().user ?: error("Log-in failed")
        } catch (e: FirebaseAuthInvalidUserException) {
            if (!createIfBuiltIn) throw e
            createBuiltIn(auth, u, password)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Newer Firebase projects report "no such user" this way too (email enumeration protection).
            if (!createIfBuiltIn) throw e
            try { createBuiltIn(auth, u, password) } catch (c: FirebaseAuthUserCollisionException) { throw e }
        }
    }

    /** First use of a built-in account: create it (keeping an anonymous phone's data for "user"). */
    private suspend fun createBuiltIn(auth: FirebaseAuth, u: String, password: String): FirebaseUser {
        val cred = EmailAuthProvider.getCredential(email(u), password)
        val current = auth.currentUser
        val result = if (u != ADMIN_USERNAME && current != null && current.isAnonymous) current.linkWithCredential(cred).await()
        else auth.createUserWithEmailAndPassword(email(u), password).await()
        return result.user ?: error("Could not create the account")
    }

    private fun offlineLogIn(u: String, password: String, builtIn: Boolean, role: Role): Result<Account> {
        if (!builtIn && !localPasswordMatches(u, password)) {
            return fail("No internet connection, and this account hasn't logged in on this phone before.")
        }
        val uid = prefs.getString(uidKey(u), null) ?: localUid(u)
        return Result.success(save(Account(uid, u, displayName(u), role), password))
    }

    /** users/{uid}: the profile the admin dashboard lists. */
    private suspend fun writeProfile(a: Account, created: Boolean = false, extra: Map<String, Any> = emptyMap()) {
        val data = mutableMapOf<String, Any>(
            "username" to a.username, "name" to a.name, "role" to a.role.name.lowercase(),
            "lastLogin" to FieldValue.serverTimestamp()
        )
        if (created) data["createdAt"] = FieldValue.serverTimestamp()
        data.putAll(extra)
        runCatching {
            withTimeout(10_000L) {
                FirebaseFirestore.getInstance().collection("users").document(a.uid).set(data, SetOptions.merge()).await()
            }
        }
    }

    /** [password] null = a Google account (no password in this app). */
    private fun save(a: Account, password: String?): Account {
        val e = prefs.edit()
            .putString(prefix + "username", a.username)
            .putString(prefix + "uid", a.uid)
            .putString(prefix + "role", a.role.name)
            .putString(uidKey(a.username), a.uid)
        if (password != null) e.putString(hashKey(a.username), hash(a.username, password))   // offline log-in next time
        // commit (written now), not apply (written later): a rider log-in restarts the app into that
        // rider's own database right after this, and a pending write would be lost (back to the welcome screen).
        e.commit()
        sessionPassword = password
        _account.value = a
        return a
    }

    private fun load(): Account? {
        val u = prefs.getString(prefix + "username", null) ?: return null
        return Account(
            prefs.getString(prefix + "uid", null) ?: localUid(u), u, displayName(u),
            runCatching { Role.valueOf(prefs.getString(prefix + "role", "USER")!!) }.getOrDefault(Role.USER),
            demo = isDemo
        )
    }

    private fun localPasswordMatches(u: String, pw: String) = prefs.getString(hashKey(u), null) == hash(u, pw)
    private fun displayName(u: String) = prefs.getString(nameKey(u), null) ?: when (u) {
        ADMIN_USERNAME -> "Administrator"; "user" -> "Rider"; else -> u.replaceFirstChar { it.uppercase() }
    }
    private fun localUid(u: String) = "local-$u"

    private fun hashKey(u: String) = "${prefix}pw_$u"
    private fun uidKey(u: String) = "${prefix}uid_$u"
    private fun nameKey(u: String) = "${prefix}name_$u"

    private fun fail(msg: String): Result<Account> = Result.failure(IllegalStateException(msg))

    private fun friendly(e: Exception): String = when (e) {
        is FirebaseAuthUserCollisionException -> "That username is taken."
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> "Wrong username or password."
        else -> e.message ?: "Something went wrong. Try again."
    }

    companion object {
        const val PREFS = "biyahe_account"
        const val ADMIN_USERNAME = "admin"
        const val EMAIL_DOMAIN = "biyahe.app"

        private data class BuiltIn(val password: String)
        /** DEMO MODE ONLY (offline sample data, never Firebase): used by Chanelly's tour on stage. */
        private val BUILT_IN = mapOf(ADMIN_USERNAME to BuiltIn("admin123"), "user" to BuiltIn("user123"))

        fun prefix(isDemo: Boolean) = if (isDemo) "demo_" else "real_"
        fun email(username: String) = "$username@$EMAIL_DOMAIN"

        /** "User.Name " -> "user.name"; null if not a valid username. */
        fun normalize(raw: String): String? = raw.trim().lowercase().takeIf { it.matches(Regex("[a-z0-9._-]{3,20}")) }

        fun hash(username: String, password: String): String =
            MessageDigest.getInstance("SHA-256").digest("biyahe:$username:$password".toByteArray())
                .joinToString("") { "%02x".format(it) }

        /** The signed-in username saved on this phone (read before the database opens). */
        fun savedUsername(context: Context, isDemo: Boolean): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefix(isDemo) + "username", null)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
