package com.biyahe.companion.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Unwraps a Compose LocalContext to the hosting Activity (or null). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
