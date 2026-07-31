package dev.dimension.flare.data.network.zhihu

/**
 * 知乎用户信息（当前登录用户，来自 /api/v4/me）
 */
internal data class ZhihuUserInfo(
    val id: String,
    val name: String,
    val urlToken: String?,
    val avatarUrl: String?,
)

/**
 * 知乎用户资料（用于关注/粉丝列表/搜索等）
 */
internal data class ZhihuPerson(
    val id: String,
    val name: String,
    val urlToken: String? = null,
    val avatarUrl: String? = null,
    val headline: String? = null,
    val gender: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val answerCount: Int = 0,
    val articlesCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
    val userType: String? = null,
)
