package dev.dimension.flare.data.network.zhihu

import kotlinx.serialization.Serializable

/**
 * 热榜条目
 */
internal data class ZhihuHotItem(
    val id: String,
    val title: String,
    val excerpt: String?,
    val answerCount: Int,
    val followerCount: Int,
    val hotValue: String,
    val url: String,
    val type: String,
    val thumbnail: String? = null,
    val createdAt: Long = 0,
)

/**
 * 知乎日报故事
 */
internal data class ZhihuDailyStory(
    val id: String,
    val title: String,
    val hint: String?,
    val imageUrl: String?,
    val url: String,
    val date: String = "",
    /** 从 /api/7/story/{id} 解析出的原始内容链接 */
    val originalUrl: String? = null,
)

/**
 * 推荐流/搜索结果/用户时间线条目
 * 支持 answer, article, video, pin, question 五种类型
 */
internal data class ZhihuFeedItem(
    val id: String,
    val type: String, // "answer", "article", "video", "pin", "question"
    val title: String,
    val excerpt: String,
    val url: String,
    val authorName: String?,
    val authorId: String?,
    val authorAvatar: String?,
    val voteCount: Int,
    val commentCount: Int,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val videoCover: String? = null,
    val videoId: String? = null,
    val videoPlayUrl: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** 内容缩略图，从 children.thumbnail 或 excerpt 中提取 */
    val thumbnail: String? = null,
)

/**
 * 关注动态响应
 */
internal data class ZhihuMomentsResponse(
    val data: List<ZhihuMomentsItem>,
    val nextOffset: String? = null,
    val isEnd: Boolean = true,
)

/**
 * 关注动态条目
 */
internal data class ZhihuMomentsItem(
    val verb: String,
    val actorName: String,
    val actorUrlToken: String?,
    val actorAvatar: String?,
    val targetType: String,
    val targetId: String,
    val title: String,
    val excerpt: String,
    val url: String,
    val createdAt: Long,
)
