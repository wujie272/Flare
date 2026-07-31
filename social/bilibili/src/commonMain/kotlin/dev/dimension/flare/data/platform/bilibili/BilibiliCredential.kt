package dev.dimension.flare.data.platform.bilibili

import kotlinx.serialization.Serializable

@Serializable
public data class BilibiliCredential(
    /** SESSDATA cookie - 登录凭证 */
    val sessdata: String? = null,
    /** bili_jct cookie - CSRF token */
    val biliJct: String? = null,
    /** buvid3 - 设备指纹 */
    val buvid3: String? = null,
    /** buvid4 - 设备指纹 v4 */
    val buvid4: String? = null,
    /** DedeUserID - 用户ID */
    val dedeUserId: String? = null,
    /** DedeUserID__ckMd5 */
    val dedeUserIdCkMd5: String? = null,
    /** sid - session id */
    val sid: String? = null,
    /** 原始 Cookie 字符串 */
    val rawCookie: String? = null,
    /** access_token - TV端登录获取，用于高画质 */
    val accessToken: String? = null,
    /** refresh_token - 刷新 access_token */
    val refreshToken: String? = null,
    /** 用户 mid */
    val mid: Long = 0,
    /** 用户名 */
    val userName: String? = null,
    /** 头像 URL */
    val avatarUrl: String? = null,
    /** 上次刷新时间 */
    val lastRefreshEpochMillis: Long? = null,
)
