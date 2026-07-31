package dev.dimension.flare.data.network.cbart.platform

import android.os.Build

internal actual fun getDeviceName(): String {
    val model = Build.MODEL ?: "unknown"
    return "$model-android"
}
