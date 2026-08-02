package dev.dimension.flare.data.datasource.coolapk

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.coolapk.CoolapkFeedItem
import dev.dimension.flare.data.network.coolapk.CoolapkService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import dev.dimension.flare.data.network.coolapk.CoolapkUser
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * 酷安搜索动态
 * 对应 API: /v6/search?type=feed&q={query}&page={page}
 */
@OptIn(ExperimentalPagingApi::class)
internal class CoolapkSearchStatusMediator(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_search_status_${query}_${accountKey.id}"
    override val supportPrepend: Boolean = false

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val page =
            when (request) {
                PagingRequest.Refresh -> 1
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
            }

        val items = service.fetchSearch(type = "feed", query = query, page = page)

        val data =
            items.mapNotNull { jsonObj: JsonObject ->
                try {
                    val item = json.decodeFromJsonElement(CoolapkFeedItem.serializer(), jsonObj)
                    item.render(accountKey)
                } catch (e: Exception) {
                    null
                }
            }

        return PagingResult(
            data = data,
            endOfPaginationReached = data.isEmpty(),
            nextKey = if (data.isNotEmpty()) (page + 1).toString() else null,
        )
    }
}

/**
 * 酷安搜索用户
 * 对应 API: /v6/search?type=user&q={query}&page={page}
 */
internal class CoolapkSearchUserPagingSource(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val page =
            when (request) {
                PagingRequest.Refresh -> 1
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
            }

        val items = service.fetchSearch(type = "user", query = query, page = page)

        val data =
            items.mapNotNull { jsonObj: JsonObject ->
                try {
                    val item = json.decodeFromJsonElement(CoolapkUser.serializer(), jsonObj)
                    item.render(accountKey)
                } catch (e: Exception) {
                    null
                }
            }

        return PagingResult(
            data = data,
            endOfPaginationReached = data.isEmpty(),
            nextKey = if (data.isNotEmpty()) (page + 1).toString() else null,
        )
    }
}
