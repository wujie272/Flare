package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class ZhihuCredential(
    /** z_c0 cookie - 登录凭证 */
    val zc0: String? = null,
    /** d_c0 cookie - 设备标识，用于签名 */
    val dc0: String? = null,
    /** xsrf token */
    val xsrfToken: String? = null,
    /** 用户 ID */
    val userId: String? = null,
    /** 用户名 */
    val userName: String? = null,
    /** 头像 URL */
    val avatarUrl: String? = null,
    /** 完整原始 Cookie 字符串 */
    val rawCookie: String? = null,
    /** 上次 Cookie 刷新时间 */
    val lastCookieRefreshEpochMillis: Long? = null,
)
