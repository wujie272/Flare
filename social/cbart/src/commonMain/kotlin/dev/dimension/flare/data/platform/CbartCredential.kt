package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class CbartCredential(
    /** 用户ID */
    val userId: String? = null,
    /** 用户名 */
    val userName: String? = null,
    /** 用户昵称 */
    val nickName: String? = null,
    /** 邮箱（用于 Laravel 站登录） */
    val email: String? = null,
    /** 头像URL */
    val avatarUrl: String? = null,
    /** 密码（用于自动登录 Laravel 站） */
    val password: String? = null,
    /** 自动登录开关 */
    val autoLogin: Boolean = true,
    /** Laravel session cookie */
    val laravelSession: String? = null,
    /** XSRF-TOKEN cookie */
    val xsrfToken: String? = null,
    /** CSRF token */
    val csrfToken: String? = null,
    /** Session 创建时间戳 */
    val sessionCreatedAt: Long? = null,
    /** 已购视频ID列表 */
    val purchasedVideoIds: String? = null,
    /** Laravel 站是否已登录 */
    val laravelSessionLoggedIn: Boolean = false,
)
