package dev.dimension.flare.data.datasource.zhihu

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.zhihu.ZhihuService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.collections.immutable.persistentListOf

internal class ZhihuLoader(
    val accountKey: MicroBlogKey,
    private val service: ZhihuService,
) : UserLoader,
    RelationLoader,
    PostLoader,
    NotificationLoader,
    NotificationBadgeProvider {
    override val supportedTypes: Set<RelationActionType> = setOf(
        RelationActionType.Follow,
    )

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val member = service.fetchMemberByUrlToken(uiHandle.normalizedRaw)
        if (member != null) return member.toUiProfile(accountKey = null)
        throw Exception("User not found: ${uiHandle.normalizedRaw}")
    }

    override suspend fun userById(id: String): UiProfile = runCatching {
        // 尝试从 API 获取真实用户信息（先当 urlToken 查，不行再当数字 ID）
        val member = runCatching {
            service.fetchMemberByUrlToken(id)
        }.getOrNull()
        if (member != null) {
            return@runCatching member.toUiProfile(accountKey = null, forceId = id)
        }
        // 兜底：从 credential 构建基本信息
        val cred = service.currentCredential()
        val userName = cred?.userName ?: id
        val avatarUrl = cred?.avatarUrl
        UiProfile(
            key = MicroBlogKey(id = id, host = "www.zhihu.com"),
            handle = UiHandle(raw = "$userName@www.zhihu.com", host = "www.zhihu.com"),
            avatar = avatarUrl?.let { dev.dimension.flare.ui.model.UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false) },
            nameInternal = userName.toUiPlainText(),
            platformId = "Zhihu",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        )
    }.getOrElse { error ->
        // 兜底兜底：任何异常都不抛，用 credential 构造一个最简 profile
        val cred = runCatching { service.currentCredential() }.getOrNull()
        val userName = cred?.userName ?: id
        val avatarUrl = cred?.avatarUrl
        UiProfile(
            key = MicroBlogKey(id = id, host = "www.zhihu.com"),
            handle = UiHandle(raw = "$userName@www.zhihu.com", host = "www.zhihu.com"),
            avatar = avatarUrl?.let { dev.dimension.flare.ui.model.UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false) },
            nameInternal = userName.toUiPlainText(),
            platformId = "Zhihu",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        )
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val memberId = userKey.id
        val response = service.fetchMemberRelation(memberId)
        if (response != null) {
            val isFollowing = response["is_following"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val isFollowed = response["is_followed"]?.jsonPrimitive?.content?.toBoolean() ?: false
            return UiRelation(
                following = isFollowing,
                isFans = isFollowed,
            )
        }
        return UiRelation()
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.followMember(userKey.id)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unfollowMember(userKey.id)
    }

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Zhihu block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Zhihu unblock is not supported")

    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Zhihu mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Zhihu unmute is not supported")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val id = statusKey.id
        return when {
            id.startsWith("article_") -> {
                val articleId = id.removePrefix("article_")
                val json = service.fetchArticleDetail(articleId)
                    ?: error("Article not found: $id")
                // 解析视频附件
                val videoInfo = json["attachment"]?.jsonObject?.let { attachment ->
                    if (attachment["type"]?.jsonPrimitive?.content == "video") {
                        val videoId = attachment["attachmentId"]?.jsonPrimitive?.content
                        if (videoId != null) {
                            service.fetchVideoPlayInfo(videoId, articleId, "article")
                        } else null
                    } else null
                }
                json.toDetailUiTimelineItem(accountKey, statusKey, videoInfo)
            }
            else -> {
                val json = service.fetchAnswerDetail(id)
                    ?: error("Answer not found: $id")
                // 解析视频附件
                val videoInfo = json["attachment"]?.jsonObject?.let { attachment ->
                    if (attachment["type"]?.jsonPrimitive?.content == "video") {
                        val videoId = attachment["attachmentId"]?.jsonPrimitive?.content
                        if (videoId != null) {
                            service.fetchVideoPlayInfo(videoId, id, "answer")
                        } else null
                    } else null
                }
                json.toDetailUiTimelineItem(accountKey, statusKey, videoInfo)
            }
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("Zhihu post deletion is not supported")

    override suspend fun notificationBadgeCount(): Int = service.fetchNotificationBadgeCount()

    override suspend fun notificationBadgeCounts(): Map<NotificationFilter, Int> =
        service.fetchNotificationBadgeCounts()
}

/**
 * 知乎通知 Loader — 支持按分类过滤
 *
 * 知乎 API 只有单个通知列表接口，过滤在客户端完成。
 * filter 为 null 时返回全部通知（All）。
 */
internal class ZhihuNotificationTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val filter: NotificationFilter? = null,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_notification_${filter?.name ?: "all"}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = service.fetchNotifications(offset = offset, limit = pageSize * 3) // 多加载一些，因为过滤后会减少
        val allItems = response.data.map { it.toNotificationUiTimelineItem(accountKey) }
        // 客户端过滤
        val filtered = if (filter != null && filter != NotificationFilter.All) {
            allItems.filter { item ->
                val itemPost = when (item) {
                    is dev.dimension.flare.ui.model.UiTimelineV2.TimelinePostItem -> item.post
                    else -> null
                }
                val category = itemPost?.card?.description?.let { desc ->
                    when {
                        desc.contains("评论") || desc.contains("回复") -> NotificationFilter.Comment
                        desc.contains("赞") || desc.contains("赞同") -> NotificationFilter.Like
                        desc.contains("关注") -> NotificationFilter.Mention
                        else -> null
                    }
                } ?: return@filter true
                category == filter
            }
        } else allItems
        return PagingResult(
            data = filtered,
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${(offset + pageSize)}" else null,
        )
    }
}

/**
 * 知乎用户时间线 Loader
 */


/**
 * 知乎关注动态 Loader
 */
internal class ZhihuMomentsTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_moments_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey
        val response = service.fetchMoments(offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = response.nextOffset,
        )
    }
}

internal class ZhihuUserTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val type: String = "answers", // "answers", "articles", "pins"
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_user_${type}_${userKey.id}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = when (type) {
            "articles" -> service.fetchUserArticles(userKey.id, offset = offset, limit = pageSize)
            "pins" -> service.fetchUserPins(userKey.id, offset = offset, limit = pageSize)
            else -> service.fetchUserAnswers(userKey.id, offset = offset, limit = pageSize)
        }
        return PagingResult(
            data = response.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${(offset + pageSize)}" else null,
        )
    }
}

/**
 * 知乎关注列表 Loader
 */
internal class ZhihuFolloweesLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "zhihu_followees_${userKey.id}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = service.fetchFollowees(userKey.id, offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toUiProfile(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${(offset + pageSize)}" else null,
        )
    }
}

/**
 * 知乎粉丝列表 Loader
 */
internal class ZhihuFollowersLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "zhihu_followers_${userKey.id}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = service.fetchFollowers(userKey.id, offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toUiProfile(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${(offset + pageSize)}" else null,
        )
    }
}

/**
 * 知乎搜索用户 Loader
 */
internal class ZhihuSearchUserLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "zhihu_search_user_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = service.searchUsers(query, offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toUiProfile(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${(offset + pageSize)}" else null,
        )
    }
}

/**
 * 推荐流分页 Loader
 */
internal class ZhihuRecommendPagingLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_recommend_paging_$accountKey"
    override val supportPrepend: Boolean = false
    private var cursor: String? = null

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        if (request is PagingRequest.Refresh) cursor = null
        val (items, nextCursor) = service.fetchRecommendFeedWithCursor(cursor, limit = pageSize)
        cursor = nextCursor
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = nextCursor == null,
            nextKey = nextCursor,
        )
    }
}

/**
 * 知乎搜索状态 Loader
 */
internal class ZhihuSearchLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_search_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val items = service.search(query, offset = offset)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = items.isEmpty(),
            nextKey = if (items.isNotEmpty()) "${offset + 20}" else null,
        )
    }
}

internal class ZhihuHotTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_hot"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchHotList()
        return PagingResult(
            data = items.mapIndexed { index, item ->
                item.toUiTimelineItem(accountKey, rank = index + 1)
            },
            endOfPaginationReached = true,
        )
    }
}

/**
 * 知乎日报 Loader — 支持按日期往前翻页
 *
 * 首次加载: /stories/latest → 取 date 作为下一页游标
 * 加载更多: /stories/before/{date} → 取返回的 date 作为下一页游标
 */
internal class ZhihuDailyTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_daily"
    override val supportPrepend: Boolean = false
    /** 当前最后加载的日期，用于 /stories/before/{date} */
    private var lastDate: String? = null

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        if (request is PagingRequest.Refresh) lastDate = null

        val (date, stories) = if (lastDate == null) {
            // 首次加载: 最新日报
            val items = service.fetchDailyStories()
            val firstDate = items.firstOrNull()?.date ?: ""
            Pair(firstDate, items)
        } else {
            // 加载更多: 往前翻
            service.fetchDailyStoriesBefore(lastDate!!)
        }

        lastDate = date

        // 并发获取每条故事的原始内容链接
        val enriched = coroutineScope {
            stories.map { story ->
                async {
                    val originalUrl = story.originalUrl
                        ?: service.fetchDailyStoryOriginalUrl(story.id)
                    story.copy(originalUrl = originalUrl)
                }
            }.map { it.await() }
        }

        return PagingResult(
            data = enriched.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = date.isEmpty(),
            nextKey = date.takeIf { it.isNotEmpty() },
        )
    }
}


/**
 * 知乎评论列表 Loader
 */
internal class ZhihuCommentsLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_comments_${statusKey.id}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        // 判断 content type: answer 或 article
        val id = statusKey.id
        val contentType: String
        val contentId: String
        when {
            id.startsWith("article_") -> { contentType = "articles"; contentId = id.removePrefix("article_") }
            else -> { contentType = "answers"; contentId = id }
        }
        val response = service.fetchComments(contentType, contentId, page = (offset / pageSize) + 1)
        return PagingResult(
            data = response.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${offset + pageSize}" else null,
        )
    }
}

/**
 * 知乎发现用户 Loader
 */
internal class ZhihuDiscoverUsersLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "zhihu_discover_users_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = service.searchUsers("", offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toUiProfile(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${offset + pageSize}" else null,
        )
    }
}

/**
 * 知乎发现话题 Loader
 */
internal class ZhihuDiscoverHashtagsLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiHashtag> {
    override val pagingKey: String = "zhihu_discover_hashtags_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiHashtag> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        return PagingResult(endOfPaginationReached = true)
    }
}

/**
 * 知乎楼中楼评论 Loader — 加载子评论
 * API: /api/v4/comment_v5/{contentType}s/{contentId}/comments?parent_id={parentId}
 */
internal class ZhihuCommentChildLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
    private val parentCommentId: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_comment_child_${statusKey.id}_${parentCommentId}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val id = statusKey.id
        val contentType: String
        val contentId: String
        when {
            id.startsWith("article_") -> { contentType = "articles"; contentId = id.removePrefix("article_") }
            else -> { contentType = "answers"; contentId = id }
        }
        val response = service.fetchChildComments(
            contentType = contentType,
            contentId = contentId,
            parentId = parentCommentId,
            page = (offset / pageSize) + 1,
        )
        return PagingResult(
            data = response.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${offset + pageSize}" else null,
        )
    }
}

/**
 * 知乎收藏列表 Loader
 * API: /api/v4/people/{urlToken}/collections → 获取收藏夹列表
 * 然后取第一个收藏夹（默认收藏夹）的内容
 */
internal class ZhihuBookmarkTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_bookmarks_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        // 先获取 urlToken
        val cred = service.currentCredential()
        val urlToken = cred?.urlToken ?: cred?.userId ?: return PagingResult(data = emptyList(), endOfPaginationReached = true)
        // 获取收藏夹列表
        val collections = service.fetchCollections(urlToken)
        if (collections.isEmpty()) return PagingResult(data = emptyList(), endOfPaginationReached = true)
        // 取第一个收藏夹的内容（默认收藏夹通常是第一个）
        val collectionId = collections.firstOrNull()?.id ?: return PagingResult(data = emptyList(), endOfPaginationReached = true)
        val response = service.fetchCollectionItems(collectionId, offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${offset + pageSize}" else null,
        )
    }
}

/**
 * 知乎点赞时间线 Loader
 * 知乎没有直接的「我赞过的」API，改用用户收藏夹的「赞过」视图
 * 或者可以用 /api/v4/members/{id}/voters 来获取用户赞过的回答
 * 目前先返回空，等后续确认 API
 */
internal class ZhihuLikeTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_likes_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        // TODO: 知乎暂无公开的「我赞过的」API
        // 可能的方案：/api/v4/members/{id}/voters?type=up 需要进一步确认
        return PagingResult(data = emptyList(), endOfPaginationReached = true)
    }
}
