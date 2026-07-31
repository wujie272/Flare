package dev.dimension.flare.data.platform.deviantart

import kotlinx.serialization.Serializable

@Serializable
public data class DeviantartCredential(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val avatarUrl: String? = null,
    val expiresIn: Long = 0,
    val lastRefreshEpochMillis: Long? = null,
    /**
     * Session cookies for _puppy API (auth, auth_secure, userinfo, _px, _pxvid, etc.)
     * Format: "auth=xxx; auth_secure=xxx; userinfo=xxx; _px=xxx; _pxvid=xxx"
     */
    val sessionCookies: String? = null,
    /**
     * CSRF token for _puppy API, obtained from window.__CSRF_TOKEN__
     */
    val csrfToken: String? = null,
)
