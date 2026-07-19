package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.*

/**
 * 公告时间线 Loader
 * 调 /api/article_list?news=1
 * 分页用页码，翻页时 page+1
 */
internal class CbartArticleTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_article"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchArticles(page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}


/**
 * 最新资源 Loader
 * 调 /api/article_list?news=0 返回最新文章/资源列表
 */
internal class CbartLatestResourceTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_latest_resource"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchLatestResources(page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 发现/推荐 Loader
 * 调 /api/studio_list 返回工作室列表
 */
internal class CbartDiscoverTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_discover"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchStudios(page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

/**
 * 热门排行榜 Loader
 */
internal class CbartHotTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_hot"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchVideos(page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

internal class CbartPurchasedVideoLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_purchased_video"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val items = service.fetchPurchasedVideos(page = page)
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            nextKey = if (items.isEmpty()) null else (page + 1).toString(),
        )
    }
}

internal class CbartGalleryCommentsLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_comments"
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> =
        notSupported<UiTimelineV2>().load(pageSize, request)
}

internal class CbartSearchUserLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "cbart_search_user"
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> =
        notSupported<UiProfile>().load(pageSize, request)
}

/**
 * 正在关注 Loader
 * 调 /api/studio_list?filter_query=xxx 返回当前用户关注的工作室列表
 */
internal class CbartFollowingLoader(
    private val service: CbartService,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "cbart_following"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val (items, total) = service.fetchFollowedStudios(page = page)
        val totalPages = (total + 19) / 20 // 向上取整
        return PagingResult(
            data = items.map { it.toUiProfile() },
            nextKey = if (page >= totalPages || items.isEmpty()) null else (page + 1).toString(),
        )
    }
}
