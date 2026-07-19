package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.data.network.cbart.api.CbartStudioItem
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.*
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

internal class CbartLoader(
    private val service: CbartService,
) : UserLoader,
    RelationLoader,
    PostLoader,
    NotificationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(
        RelationActionType.Follow,
    )

    override suspend fun notificationBadgeCount(): Int = 0 // Cbart 没有未读通知计数 API

    override suspend fun follow(userKey: MicroBlogKey) {
        val studioId = userKey.id.toLongOrNull() ?: throw UnsupportedOperationException("Cbart follow requires numeric studio ID")
        service.toggleFollow(0, studioId, follow = true)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        val studioId = userKey.id.toLongOrNull() ?: throw UnsupportedOperationException("Cbart unfollow requires numeric studio ID")
        service.toggleFollow(0, studioId, follow = false)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val studioId = userKey.id.toLongOrNull()
        if (studioId != null) {
            val isFollowed = service.isStudioFollowed(studioId)
            return UiRelation(following = isFollowed)
        }
        return UiRelation()
    }

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        throw UnsupportedOperationException("Cbart user lookup by handle is not supported")

    override suspend fun userById(id: String): UiProfile {
        // 从 credential 构建用户信息，避免抛出异常导致账户列表显示"加载失败"
        val cred = service.currentCredential()
        val userInfo = service.fetchCurrentUser()
        val username = userInfo?.username ?: cred?.userName ?: id.removePrefix("cb_").substringBefore("_")
        val nickName = userInfo?.nickName ?: cred?.nickName ?: username

        // 尝试获取关注的工作室数量
        val followsCount = try {
            service.fetchFollowedStudioCount()
        } catch (_: Exception) {
            0
        }

        return UiProfile(
            key = MicroBlogKey(id = id, host = "cbart.net"),
            handle = UiHandle(
                raw = "$username@cbart.net",
                host = "cbart.net",
            ),
            avatar = (userInfo?.avatarUrl ?: cred?.avatarUrl).toUiImage(),
            nameInternal = nickName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(
                fansCount = 0,
                followsCount = followsCount.toLong(),
                statusesCount = 0,
            ),
            mark = persistentListOf(),
            bottomContent = null,
        )
    }

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart unblock is not supported")

    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart unmute is not supported")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        throw UnsupportedOperationException("Cbart status detail is not supported - use WebView to open content")

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart post deletion is not supported")
}

// ========== 用户时间线 Loader ==========

internal class CbartUserContentLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_user_content_${userKey.id}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchUserContent(uid = userKey.id, page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

// ========== 通知 Loader ==========

internal class CbartNotificationTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_notification_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchMessages(page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

// ========== 搜索工作室 Loader ==========

internal class CbartStudioSearchLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_studio_search_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.searchStudios(keyword = query, page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}
