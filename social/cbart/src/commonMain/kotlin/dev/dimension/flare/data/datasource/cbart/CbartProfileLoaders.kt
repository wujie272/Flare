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
        val fromUid = runCatching { service.fetchNumericUid() }.getOrNull() ?: 0L
        service.toggleFollow(fromUid, studioId, follow = true)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        val studioId = userKey.id.toLongOrNull() ?: throw UnsupportedOperationException("Cbart unfollow requires numeric studio ID")
        val fromUid = runCatching { service.fetchNumericUid() }.getOrNull() ?: 0L
        service.toggleFollow(fromUid, studioId, follow = false)
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
        // 双层 runCatching 兜底：外层捕获所有异常，内层捕获 fetchUserByUid 异常
        val owner = runCatching {
            runCatching { service.fetchUserByUid(id) }.getOrNull()
        }.getOrNull()
        if (owner != null) {
            val name = owner.nickName ?: owner.displayName ?: owner.username ?: id
            val avatarUrl = owner.avatarUrl ?: owner.avatar?.let { "https://www.tpzf001.com$it" }
            return UiProfile(
                key = MicroBlogKey(id = id, host = "cbart.net"),
                handle = UiHandle(raw = "$name@cbart.net", host = "cbart.net"),
                avatar = avatarUrl.toUiImage(),
                nameInternal = name.toUiPlainText(),
                platformType = PlatformType.Cbart,
                clickEvent = ClickEvent.Noop,
                banner = null,
                description = null,
                matrices = UiProfile.Matrices(
                    fansCount = (owner.followerNum ?: 0).toLong(),
                    followsCount = 0,
                    statusesCount = 0,
                ),
                mark = persistentListOf(),
                bottomContent = null,
            )
        }

        // 兜底：从 credential 构建用户信息
        val cred = try { service.currentCredential() } catch (_: Exception) { null }
        val userInfo = try { service.fetchCurrentUser() } catch (_: Exception) { null }
        val username = userInfo?.username ?: cred?.userName ?: id.removePrefix("cb_").substringBefore("_")
        val nickName = userInfo?.nickName ?: cred?.nickName ?: username

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
        // 如果 userKey.id 是非数字的 fallback 格式，尝试解析为数字 uid
        val uid = userKey.id
        val resolvedUid = if (uid.toLongOrNull() != null) {
            uid
        } else {
            // 非数字 uid → 尝试从 profile 获取真实数字 uid
            try {
                service.fetchNumericUid()?.toString() ?: uid
            } catch (_: Exception) {
                uid
            }
        }
        val items = service.fetchUserContent(uid = resolvedUid, page = page)
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
