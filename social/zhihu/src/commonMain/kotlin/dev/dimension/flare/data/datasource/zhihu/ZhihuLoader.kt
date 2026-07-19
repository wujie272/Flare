package dev.dimension.flare.data.datasource.zhihu

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
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
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.json.jsonPrimitive
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf

internal class ZhihuLoader(
    private val service: ZhihuService,
) : UserLoader,
    RelationLoader,
    PostLoader,
    NotificationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(
        RelationActionType.Follow,
    )

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val member = service.fetchMemberByUrlToken(uiHandle.normalizedRaw)
        if (member != null) return member.toUiProfile(accountKey = null)
        throw Exception("User not found: ${uiHandle.normalizedRaw}")
    }

    override suspend fun userById(id: String): UiProfile {
        // 尝试从 API 获取真实用户信息（先当 urlToken 查，不行再当数字 ID）
        val member = runCatching {
            service.fetchMemberByUrlToken(id)
        }.getOrNull()
        if (member != null) {
            return member.toUiProfile(accountKey = null)
        }
        // 兜底：从 credential 构建基本信息
        val cred = service.currentCredential()
        val userName = cred?.userName ?: id
        val avatarUrl = cred?.avatarUrl
        return UiProfile(
            key = MicroBlogKey(id = id, host = "www.zhihu.com"),
            handle = UiHandle(raw = "$userName@www.zhihu.com", host = "www.zhihu.com"),
            avatar = avatarUrl?.let { dev.dimension.flare.ui.model.UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false) },
            nameInternal = userName.toUiPlainText(),
            platformType = PlatformType.Zhihu,
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

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        throw UnsupportedOperationException("Zhihu status detail is not supported - use WebView to open article")

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("Zhihu post deletion is not supported")

    override suspend fun notificationBadgeCount(): Int = service.fetchNotificationBadgeCount()
}

/**
 * 知乎通知 Loader
 */
internal class ZhihuNotificationTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_notification_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val response = service.fetchNotifications(offset = offset, limit = pageSize)
        return PagingResult(
            data = response.data.map { it.toNotificationUiTimelineItem(accountKey) },
            endOfPaginationReached = response.isEnd,
            nextKey = if (!response.isEnd) "${(offset + pageSize)}" else null,
        )
    }
}

/**
 * 知乎用户时间线 Loader
 */
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
 * 知乎日报 Loader
 */
internal class ZhihuDailyTimelineLoader(
    private val service: ZhihuService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "zhihu_daily"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchDailyStories()
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = true,
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
