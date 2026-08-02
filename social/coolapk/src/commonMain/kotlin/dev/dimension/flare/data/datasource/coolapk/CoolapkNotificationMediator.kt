package dev.dimension.flare.data.datasource.coolapk

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.coolapk.CoolapkNotificationItem
import dev.dimension.flare.data.network.coolapk.CoolapkService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * 酷安通知分页加载器
 * 对应 API: /v6/notification/list?type={type}&page={page}
 */
@OptIn(ExperimentalPagingApi::class)
internal class CoolapkNotificationMediator(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val type: String,
    private val onClearMarker: suspend () -> Unit,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_notification_${type}_${accountKey.id}"
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

        val items = service.fetchNotificationList(type = type, page = page)

        if (request == PagingRequest.Refresh) {
            onClearMarker()
        }

        val data =
            items.mapNotNull { jsonObj: JsonObject ->
                try {
                    val item = json.decodeFromJsonElement(CoolapkNotificationItem.serializer(), jsonObj)
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
 * 酷安通知列表（全部类型混合）
 */
@OptIn(ExperimentalPagingApi::class)
internal class CoolapkAllNotificationMediator(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: suspend () -> Unit,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_notification_all_${accountKey.id}"
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

        // 全部通知 = 遍历所有类型，去重合并
        val allTypes = listOf("atme", "reply", "comment", "like", "follow", "system")
        val allItems =
            allTypes.flatMap { type: String ->
                service.fetchNotificationList(type = type, page = page)
            }.distinctBy { it["id"]?.toString() }

        if (request == PagingRequest.Refresh) {
            onClearMarker()
        }

        val data =
            allItems.mapNotNull { jsonObj: JsonObject ->
                try {
                    val item = json.decodeFromJsonElement(CoolapkNotificationItem.serializer(), jsonObj)
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
