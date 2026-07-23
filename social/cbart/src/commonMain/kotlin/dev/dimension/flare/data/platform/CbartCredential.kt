package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class CbartCredential(
    /** Laravel session cookie */
    val laravelSession: String,
    /** XSRF-TOKEN cookie */
    val xsrfToken: String? = null,
    /** 用户ID */
    val userId: String? = null,
    /** 用户名（username） */
    val userName: String? = null,
    /** 用户昵称 */
    val nickName: String? = null,
    /** 头像URL */
    val avatarUrl: String? = null,
    /** 上次 session 刷新时间戳 */
    val lastSessionRefreshEpochMillis: Long? = null,
)
