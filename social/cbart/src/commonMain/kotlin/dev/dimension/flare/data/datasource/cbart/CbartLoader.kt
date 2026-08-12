package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.data.platform.CbartCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.async
import dev.dimension.flare.data.network.cbart.api.LzjVideoDetailItem
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf

/**
 * 公告时间线 Loader
 * 调 linzijun.app/api/article_list
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
        val articles = service.fetchArticles(page = page, limit = pageSize)
        return PagingResult(
            data = articles.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (articles.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 视频时间线 Loader
 * 调 linzijun.app/api/video_list
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
        val items = service.fetchVideoList(page = page, limit = pageSize, order = "posttime")
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 图集时间线 Loader（今日更新）
 */
internal class CbartPictureTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_today"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchVideoList(page = page, limit = pageSize, order = "posttime", filterOneday = true)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 作者/工作室时间线 Loader（Laravel 站无对应 API，返回空）
 */
internal class CbartProducerTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_producers"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> =
        PagingResult(endOfPaginationReached = true, data = emptyList())
}

/**
 * 通知/消息时间线 Loader（Laravel 站无对应 API，返回空）
 */
internal class CbartNotificationTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_notification"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> =
        PagingResult(endOfPaginationReached = true, data = emptyList())
}

/**
 * 评论 Loader（Laravel 站无对应 API，返回空）
 */
internal class CbartCommentsLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
    private val videoId: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_comments_$videoId"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> =
        PagingResult(endOfPaginationReached = true, data = emptyList())
}

/**
 * 搜索 Loader
 * 调 linzijun.app/api/video_list?keyword=xxx
 */
internal class CbartSearchLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_search_${query.hashCode()}"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchVideoList(page = page, limit = pageSize, order = "posttime", keyword = query)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 收藏视频 Loader
 * 调 linzijun.app/api/video_list?fav=1
 */
internal class CbartFavVideoLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_fav_video"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchVideoList(page = page, limit = pageSize, fav = true)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 已购视频 Loader
 * 调 linzijun.app/api/video_list?purchased_video=1
 */
internal class CbartPurchasedVideoLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_purchased_video"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchVideoList(page = page, limit = pageSize, order = "posttime", purchased = true)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 用户内容 Loader
 * 调 linzijun.app/api/video_list
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
        val items = service.fetchVideoList(page = page, limit = pageSize, order = "posttime")
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 搜索用户 Loader（无 API，永远空）
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
 * 福利 Loader（Laravel 站无对应 API，返回空）
 */
internal class CbartFuliLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "lzj_fuli"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> =
        PagingResult(endOfPaginationReached = true, data = emptyList())
}

/**
 * 关注列表 Loader（Laravel 站无对应 API，返回空）
 */
internal class CbartFollowingLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "lzj_following"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> =
        PagingResult(endOfPaginationReached = true, data = emptyList())
}
