package dev.dimension.flare.data.datasource.coolapk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.coolapk.CoolapkFeedItem
import dev.dimension.flare.data.network.coolapk.CoolapkBlockInfo
import dev.dimension.flare.data.network.coolapk.CoolapkService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

// ==================== 首页时间线 ====================

internal class CoolapkHomeTimelineLoader(
    private val accountKey: MicroBlogKey,
    private val service: CoolapkService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_home_${accountKey.id}"
    override val supportPrepend: Boolean = false
    private var page = 1

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        return when (request) {
            PagingRequest.Refresh -> {
                page = 1
                val items = service.fetchMainIndex(page = page)
                val timeline = items.flatMap { card -> card.toTimelineItems(accountKey) }
                page++
                PagingResult(data = timeline, nextKey = if (items.isEmpty()) null else page.toString())
            }
            is PagingRequest.Append -> {
                val items = service.fetchMainIndex(page = page)
                val timeline = items.flatMap { card -> card.toTimelineItems(accountKey) }
                page++
                PagingResult(data = timeline, nextKey = if (items.isEmpty()) null else page.toString())
            }
        }
    }
}

// ==================== 关注动态 ====================

internal class CoolapkFollowTimelineLoader(
    private val accountKey: MicroBlogKey,
    private val service: CoolapkService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_follow_${accountKey.id}"
    override val supportPrepend: Boolean = false
    private var lastItem: String? = null
    private var page = 1

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        return when (request) {
            PagingRequest.Refresh -> {
                page = 1
                lastItem = null
                val items = service.fetchFollowFeed(page = page)
                val feedItems = items.mapNotNull { it.toFeedItem(accountKey) }
                lastItem =
                    items
                        .lastOrNull()
                        ?.get("id")
                        ?.jsonPrimitive
                        ?.content
                page++
                PagingResult(data = feedItems, nextKey = if (items.isEmpty()) null else page.toString())
            }
            is PagingRequest.Append -> {
                val items = service.fetchFollowFeed(page = page, lastItem = lastItem)
                val feedItems = items.mapNotNull { it.toFeedItem(accountKey) }
                lastItem =
                    items
                        .lastOrNull()
                        ?.get("id")
                        ?.jsonPrimitive
                        ?.content
                page++
                PagingResult(data = feedItems, nextKey = if (items.isEmpty()) null else page.toString())
            }
        }
    }
}

// ==================== 酷图浏览 ====================

internal class CoolapkCoolPicTimelineLoader(
    private val accountKey: MicroBlogKey,
    private val service: CoolapkService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "coolapk_coolpic_${accountKey.id}"
    override val supportPrepend: Boolean = false
    private var page = 1

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        return when (request) {
            PagingRequest.Refresh -> {
                page = 1
                val items = service.fetchCoolPic(page = page)
                val feedItems = items.mapNotNull { it.toFeedItem(accountKey) }
                page++
                PagingResult(data = feedItems, nextKey = if (items.isEmpty()) null else page.toString())
            }
            is PagingRequest.Append -> {
                val items = service.fetchCoolPic(page = page)
                val feedItems = items.mapNotNull { it.toFeedItem(accountKey) }
                page++
                PagingResult(data = feedItems, nextKey = if (items.isEmpty()) null else page.toString())
            }
        }
    }
}

// ==================== 数据解析 ====================

private fun kotlinx.serialization.json.JsonObject.toTimelineItems(accountKey: MicroBlogKey): List<UiTimelineV2> {
    val template = get("entityTemplate")?.jsonPrimitive?.content ?: return emptyList()
    val entities = get("entities")?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()

    return when (template) {
        "feed", "feedCover" -> listOfNotNull(this.toFeedItem(accountKey))
        "imageCarouselCard_1" ->
            entities.mapNotNull { entity ->
                val title = entity["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val pic = entity["pic"]?.jsonPrimitive?.content ?: ""
                val feedItem =
                    CoolapkFeedItem(
                        id = title.hashCode().toLong(),
                        message = title,
                        title = title,
                        pic = pic,
                        username = "酷安",
                        userAvatar = "",
                        uid = 0,
                    )
                feedItem.render(accountKey)
            }
        else -> emptyList()
    }
}

private fun kotlinx.serialization.json.JsonObject.toFeedItem(accountKey: MicroBlogKey): UiTimelineV2? =
    try {
        // 手动解析，兼容酷安API返回数字有时是字符串的奇葩行为
        val feedItem = CoolapkFeedItem(
            id = getPrimitiveString("id")?.toLongOrNull() ?: 0L,
            message = getPrimitiveString("message") ?: "",
            username = getPrimitiveString("username") ?: "",
            uid = getPrimitiveString("uid")?.toLongOrNull() ?: 0L,
            userAvatar = getPrimitiveString("userAvatar") ?: "",
            createdAt = getPrimitiveString("dateline")?.toLongOrNull() ?: 0L,
            pic = getPrimitiveString("pic") ?: "",
            type = getPrimitiveString("feedTypeName") ?: "",
            blockInfo = get("blockInfo")?.let { json.decodeFromJsonElement(CoolapkBlockInfo.serializer(), it) },
            likeCount = getPrimitiveString("likenum")?.toIntOrNull() ?: 0,
            replyCount = getPrimitiveString("replynum")?.toIntOrNull() ?: 0,
            favoriteCount = getPrimitiveString("favnum")?.toIntOrNull() ?: 0,
            shareCount = getPrimitiveString("sharenum")?.toIntOrNull() ?: 0,
            isLiked = getPrimitiveString("is_liked")?.toIntOrNull() ?: 0,
            entityTemplate = getPrimitiveString("entityTemplate") ?: "",
            title = getPrimitiveString("title") ?: "",
            url = getPrimitiveString("url") ?: "",
            deviceTitle = getPrimitiveString("device_title") ?: "",
            info = getPrimitiveString("info") ?: "",
            infoHtml = getPrimitiveString("infoHtml") ?: "",
        )
        feedItem.render(accountKey)
    } catch (e: Exception) {
        null
    }

/**
 * 从 JSON 对象中获取字段的字符串表示，兼容数字和字符串类型。
 * JsonPrimitive.content 对数字 123 返回 "123"，对字符串 "abc" 返回 "abc"。
 */
private fun kotlinx.serialization.json.JsonObject.getPrimitiveString(key: String): String? =
    get(key)?.jsonPrimitive?.content
