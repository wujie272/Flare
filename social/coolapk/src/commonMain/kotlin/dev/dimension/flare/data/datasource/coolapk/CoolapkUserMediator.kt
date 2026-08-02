package dev.dimension.flare.data.datasource.coolapk

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.coolapk.CoolapkFeedItem
import dev.dimension.flare.data.network.coolapk.CoolapkService
import dev.dimension.flare.data.network.coolapk.CoolapkUser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * 用户时间线
 * 对应 API: /v6/feed/list?type=feed&uid={uid}&page={page}
 */
internal class CoolapkUserTimelineMediator(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val mediaOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_user_timeline_${userKey.id}_${accountKey.id}"
    override val supportPrepend: Boolean = false

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        if (mediaOnly) {
            return PagingResult(endOfPaginationReached = true)
        }

        val page =
            when (request) {
                PagingRequest.Refresh -> 1
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
            }

        val items = service.fetchUserFeed(uid = userKey.id, page = page, pageSize = pageSize)

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
            nextKey = if (data.isNotEmpty()) (page + 1).toString() else null,
        )
    }
}

/**
 * 关注列表
 * 对应 API: /v6/user/followList?uid={uid}&page={page}
 */
internal class CoolapkFollowingPagingSource(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
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

        val items = service.fetchFollowList(uid = userKey.id, page = page)

        val data =
            items.mapNotNull { jsonObj: JsonObject ->
                try {
                    // followList 返回的 item 包含 fUserInfo
                    val userObj = jsonObj["fUserInfo"]?.jsonObject ?: jsonObj
                    val item = json.decodeFromJsonElement(CoolapkUser.serializer(), userObj)
                    item.render(accountKey)
                } catch (e: Exception) {
                    null
                }
            }

        return PagingResult(
            data = data,
            nextKey = if (data.isNotEmpty()) (page + 1).toString() else null,
        )
    }
}

/**
 * 粉丝列表
 * 对应 API: /v6/user/fansList?uid={uid}&page={page}
 */
internal class CoolapkFansPagingSource(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
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

        val items = service.fetchFansList(uid = userKey.id, page = page)

        val data =
            items.mapNotNull { jsonObj: JsonObject ->
                try {
                    val userObj = jsonObj["fUserInfo"]?.jsonObject ?: jsonObj
                    val item = json.decodeFromJsonElement(CoolapkUser.serializer(), userObj)
                    item.render(accountKey)
                } catch (e: Exception) {
                    null
                }
            }

        return PagingResult(
            data = data,
            nextKey = if (data.isNotEmpty()) (page + 1).toString() else null,
        )
    }
}

/**
 * 单条动态详情
 * 对应 API: /v6/feed/detail?id={id}
 */
internal class CoolapkContextMediator(
    private val service: CoolapkService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_context_${statusKey.id}_${accountKey.id}"
    override val supportPrepend: Boolean = false

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val detail = service.fetchFeedDetail(id = statusKey.id)

        val data =
            if (detail != null) {
                try {
                    val item = json.decodeFromJsonElement(CoolapkFeedItem.serializer(), detail)
                    listOf(item.render(accountKey))
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

        // 单条详情只有一页
        return PagingResult(
            data = data,
            nextKey = null,
        )
    }
}
