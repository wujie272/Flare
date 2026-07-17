package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class CbartCredential(
    /** PHPSESSID session cookie */
    val sessionId: String,
    /** 用户ID */
    val userId: String? = null,
    /** 用户名（username） */
    val userName: String? = null,
    /** 用户昵称 */
    val nickName: String? = null,
    /** 头像URL */
    val avatarUrl: String? = null,
    /** Cloudflare clearance cookie（免重复验证） */
    val cfClearance: String? = null,
)
