package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class CbartCredential(
    /** 妖狐吧 API Token（硬编码在 App 中） */
    val apiToken: String = "ACF09D095C44ADD56B80FEE4A3A5BB3A",
    /** 用户ID（注册后返回的 uid） */
    val userId: String? = null,
    /** 用户名 */
    val userName: String? = null,
    /** 用户昵称 */
    val nickName: String? = null,
    /** 头像URL */
    val avatarUrl: String? = null,
    /** 设备 UUID */
    val uuid: String? = null,
    /** 密码 */
    val password: String? = null,
    /** 当前活跃的备用域名 */
    val activeUrl: String = "https://shenmatk.com",
    /** 自动登录开关 */
    val autoLogin: Boolean = true,
)
