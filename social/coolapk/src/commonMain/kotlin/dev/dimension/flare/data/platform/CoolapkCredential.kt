package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class CoolapkCredential(
    val token: String = "",
    val username: String = "",
    val uid: String = "",
    val rawCookie: String = "",
    val deviceCode: String = "",
    val appToken: String = "",
)
