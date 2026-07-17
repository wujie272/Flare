package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class ToutiaoCredential(
    /** ttwid cookie */
    val ttwid: String? = null,
    /** csrftoken cookie */
    val csrftoken: String? = null,
    /** tt_webid cookie */
    val ttWebid: String? = null,
    /** User ID (from personal page) */
    val userId: String? = null,
    /** Display name */
    val userName: String? = null,
    /** Avatar URL */
    val avatarUrl: String? = null,
    /** Full raw cookie string, stored for pass-through */
    val rawCookie: String? = null,
)
