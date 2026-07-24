package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

/**
 * 公告时间线 Loader
 * 调 /api/articles.php
 */
internal class CbartArticleTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_article"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchArticles(page = page, limit = pageSize)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 视频时间线 Loader
 * 调 /api/video_list.php
 */
internal class CbartVideoTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_video"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchVideos(page = page, limit = pageSize)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 图片时间线 Loader
 * 调 /api/group_pics.php
 */
internal class CbartPictureTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_pictures"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchPictureGroups(page = page, limit = pageSize)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 作者/工作室时间线 Loader
 * 调 /api/producer.php
 */
internal class CbartProducerTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_producers"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchProducers(page = page, limit = pageSize)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 通知/消息时间线 Loader
 * 调 /api/get_message.php
 */
internal class CbartNotificationTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_notification"
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

/**
 * 评论 Loader
 */
internal class CbartCommentsLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
    private val videoId: Long,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_comments_$videoId"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchComments(videoId = videoId, page = 1)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = true,
        )
    }
}

/**
 * 用户内容 Loader（作者的视频/图片）
 */
internal class CbartUserContentLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_user_content_${userKey.id}"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        // 妖狐吧没有直接按 uid 查内容的 API，用视频列表代替
        val items = service.fetchVideos(page = page, limit = pageSize)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 搜索用户/工作室 Loader
 */
internal class CbartSearchUserLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "lzj_search_user"
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> =
        PagingResult(endOfPaginationReached = true, data = emptyList())
}

/**
 * 关注列表 Loader
 */
internal class CbartFuliLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_fuli"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val fuliData = service.fetchDailyFuli()
        val item = if (fuliData != null) {
            listOf(fuliData.toUiTimelineItem(accountKey))
        } else {
            emptyList()
        }
        return PagingResult(data = item, endOfPaginationReached = true)
    }
}

internal class CbartFollowingLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "lzj_following"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        return PagingResult(endOfPaginationReached = true, data = emptyList())
    }
}
