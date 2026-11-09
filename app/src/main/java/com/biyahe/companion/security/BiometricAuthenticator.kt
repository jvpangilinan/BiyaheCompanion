package com.biyahe.companion.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class Availability { UNKNOWN, AVAILABLE, NO_HARDWARE, NONE_ENROLLED, UNAVAILABLE }

/**
 * Wraps AndroidX BiometricPrompt. Fingerprint is the primary method; on
 * Android 11+ the device PIN/pattern is allowed as a backup.
 */
class BiometricAuthenticator(private val context: Context) {

    private val allowedAuthenticators: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK

    private val deviceCredentialAllowed get() = allowedAuthenticators and DEVICE_CREDENTIAL != 0

    fun availability(): Availability =
        when (BiometricManager.from(context).canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.UNAVAILABLE
        }

    /** Opens the system screen where the user can register a fingerprint. */
    fun enrollmentIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, allowedAuthenticators
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailedAttempt: () -> Unit,
        onError: (userCancelled: Boolean, message: String) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationFailed() = onFailedAttempt()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(cancelled, errString.toString())
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .apply { if (!deviceCredentialAllowed) setNegativeButtonText("Cancel") }
            .build()
        prompt.authenticate(info)
    }
}
