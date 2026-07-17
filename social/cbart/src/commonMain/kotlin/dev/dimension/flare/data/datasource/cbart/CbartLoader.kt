package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

internal class CbartHomeTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_home"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchHomePage()
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = true,
        )
    }
}

internal class CbartDiscoverTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_discover"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        return PagingResult(data = service.fetchHomePage().map { it.toUiTimelineItem(accountKey) }, endOfPaginationReached = true)
    }
}

internal class CbartFavouriteTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_fav"
    override val supportPrepend: Boolean = false
    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchFavorites()
        return PagingResult(data = items.map { it.toUiTimelineItem(accountKey) }, endOfPaginationReached = true)
    }
}

/**
 * 热门排行榜 Loader
 * 使用 /api/user_original_stats.php 返回的创作者排行数据
 * 映射为 `UiTimelineV2` 条目
 */
internal class CbartHotTimelineLoader(
    private val service: CbartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "cbart_hot"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val stats = service.api.getStats(period = "week", order = "earned", limit = 20)
        val items = stats?.stats ?: emptyList()
        return PagingResult(
            data = items.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = true,
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
