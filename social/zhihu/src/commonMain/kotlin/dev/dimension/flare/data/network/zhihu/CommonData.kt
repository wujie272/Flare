package dev.dimension.flare.data.network.zhihu

/**
 * 知乎评论
 */
internal data class ZhihuComment(
    val id: String,
    val content: String,
    val authorName: String?,
    val authorId: String?,
    val authorAvatar: String?,
    val likeCount: Int,
    val createdAt: Long,
    /** 回复目标的用户名，楼中楼评论时不为空 */
    val replyToAuthorName: String? = null,
)

/**
 * 知乎通知项
 */
internal data class ZhihuNotificationItem(
    val id: String,
    val type: String,
    val isRead: Boolean = false,
    val createTime: Long = 0,
    val verb: String = "",
    val actorName: String? = null,
    val actorLink: String? = null,
    val targetText: String? = null,
    val targetLink: String? = null,
    val targetTitle: String? = null,
    val targetType: String? = null,
) {
    /** 通知分类：comment / follow / vote 等 */
    val notificationCategory: String get() = when {
        verb.contains("评论") -> "comment"
        verb.contains("关注") || verb.contains("关注了你") -> "follow"
        verb.contains("赞") || verb.contains("赞同") || verb.contains("感谢") -> "vote"
        else -> "other"
    }
}

/**
 * 视频播放信息
 */
/**
 * 知乎收藏夹
 */
internal data class ZhihuCollection(
    val id: String,
    val title: String,
    val itemCount: Int = 0,
    val isDefault: Boolean = false,
)

internal data class ZhihuVideoInfo(
    val videoId: String,
    val url: String? = null,
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 通用分页响应
 */
internal data class ZhihuPagingResponse<T>(
    val data: List<T>,
    val isEnd: Boolean = true,
    val nextOffset: Int? = null,
    val total: Int = 0,
)
