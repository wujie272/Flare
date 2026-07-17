package dev.dimension.flare.data.datasource.toutiao

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.toutiao.ToutiaoService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class ToutiaoLoader(
    private val service: ToutiaoService,
) : UserLoader,
    RelationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = emptySet()

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        throw UnsupportedOperationException("Toutiao user lookup by handle is not supported")

    override suspend fun userById(id: String): UiProfile =
        throw UnsupportedOperationException("Toutiao user lookup by id is not supported")

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun follow(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao follow is not supported")

    override suspend fun unfollow(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao unfollow is not supported")

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao unblock is not supported")

    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao unmute is not supported")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        throw UnsupportedOperationException("Toutiao status detail is not supported - use WebView to open article")

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("Toutiao post deletion is not supported")
}

/**
 * 今日头条热榜 Loader
 */
internal class ToutiaoHotTimelineLoader(
    private val service: ToutiaoService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "toutiao_hot"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchHotBoard()
        return PagingResult(
            data = items.mapIndexed { index, item ->
                item.toUiTimelineItem(accountKey, rank = index + 1)
            },
            endOfPaginationReached = true,
        )
    }
}

/**
 * 今日头条推荐/发现 Loader
 */
internal class ToutiaoRecommendTimelineLoader(
    private val service: ToutiaoService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "toutiao_recommend"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.fetchRecommendedNews()
        return PagingResult(
            data = items.mapIndexed { index, item ->
                item.toUiTimelineItem(accountKey, rank = index + 1)
            },
            endOfPaginationReached = true,
        )
    }
}
