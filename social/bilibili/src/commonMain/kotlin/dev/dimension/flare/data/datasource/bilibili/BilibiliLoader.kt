package dev.dimension.flare.data.datasource.bilibili

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bilibili.BilibiliService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// ==================== 数据模型 ====================

internal data class BilibiliUser(
    val mid: Long, val name: String, val face: String, val sign: String,
    val fans: Int, val follows: Int, val archiveCount: Int,
) {
    companion object {
        fun fromJson(obj: kotlinx.serialization.json.JsonObject): BilibiliUser? = try {
            BilibiliUser(
                mid = obj["mid"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null,
                name = obj["name"]?.jsonPrimitive?.content ?: obj["uname"]?.jsonPrimitive?.content ?: "",
                face = (obj["face"]?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
                sign = obj["sign"]?.jsonPrimitive?.content ?: obj["usign"]?.jsonPrimitive?.content ?: "",
                fans = obj["fans"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: obj["follower"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                follows = obj["follows"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: obj["following"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                archiveCount = obj["archive_count"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: obj["video_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        } catch (_: Exception) { null }
    }
}

internal data class BilibiliVideoStat(
    val view: Long = 0, val like: Long = 0, val favorite: Long = 0, val danmaku: Long = 0,
) {
    companion object {
        fun fromJson(obj: kotlinx.serialization.json.JsonObject): BilibiliVideoStat? = try {
            BilibiliVideoStat(
                view = obj["view"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                like = obj["like"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                favorite = obj["favorite"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                danmaku = obj["danmaku"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        } catch (_: Exception) { null }
    }
}

internal data class BilibiliFeedItem(
    val bvid: String, val title: String, val pic: String, val duration: Long,
    val ownerName: String, val ownerFace: String, val ownerMid: Long,
    val stat: BilibiliVideoStat?, val pubdate: Long,
) {
    companion object {
        fun fromJson(obj: kotlinx.serialization.json.JsonObject): BilibiliFeedItem? = try {
            val bvid = obj["bvid"]?.jsonPrimitive?.content ?: return null
            val owner = obj["owner"]?.jsonObject
            BilibiliFeedItem(
                bvid = bvid,
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                pic = (obj["pic"]?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
                duration = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                ownerName = owner?.get("name")?.jsonPrimitive?.content
                    ?: obj["author"]?.jsonPrimitive?.content ?: "",
                ownerFace = (owner?.get("face")?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
                ownerMid = owner?.get("mid")?.jsonPrimitive?.content?.toLongOrNull()
                    ?: obj["mid"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                stat = obj["stat"]?.jsonObject?.let { BilibiliVideoStat.fromJson(it) },
                pubdate = obj["pubdate"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: obj["ctime"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        } catch (_: Exception) { null }
    }
}

internal data class BilibiliVideoInfo(
    val bvid: String, val title: String, val pic: String, val description: String,
    val ownerName: String, val ownerFace: String, val ownerMid: Long,
    val stat: BilibiliVideoStat?, val pubdate: Long, val cid: Long = 0,
) {
    companion object {
        fun fromJson(obj: kotlinx.serialization.json.JsonObject): BilibiliVideoInfo? = try {
            val bvid = obj["bvid"]?.jsonPrimitive?.content ?: return null
            val owner = obj["owner"]?.jsonObject
            BilibiliVideoInfo(
                bvid = bvid,
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                pic = (obj["pic"]?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
                description = obj["desc"]?.jsonPrimitive?.content ?: "",
                ownerName = owner?.get("name")?.jsonPrimitive?.content ?: "",
                ownerFace = (owner?.get("face")?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
                ownerMid = owner?.get("mid")?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                stat = obj["stat"]?.jsonObject?.let { BilibiliVideoStat.fromJson(it) },
                pubdate = obj["pubdate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                cid = obj["cid"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        } catch (_: Exception) { null }
    }
}

// ==================== Mapper 辅助函数 ====================

private fun BilibiliFeedItem.toUiTimelineV2(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = bvid, host = "bilibili.com")
    val userKey = MicroBlogKey(id = ownerMid.toString(), host = "bilibili.com")
    val media = if (pic.isNotEmpty()) {
        listOf(UiMedia.Image(url = pic, previewUrl = pic, description = title, width = 0f, height = 0f, sensitive = false))
    } else emptyList()

    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = ownerName, host = "bilibili.com"),
        avatar = UiMedia.Image(url = ownerFace, previewUrl = ownerFace, description = ownerName, width = 0f, height = 0f, sensitive = false),
        nameInternal = ownerName.toUiPlainText(),
        platformType = PlatformType.Bilibili,
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null, description = null, matrices = UiProfile.Matrices(0, 0, 0), mark = persistentListOf(), bottomContent = null,
    )

    val card = UiCard(media = media.firstOrNull(), title = title, description = null, url = "https://www.bilibili.com/video/$bvid")

    return UiTimelineV2.Post(
        platformType = PlatformType.Bilibili,
        images = media.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(original = title.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = card,
        createdAt = Instant.fromEpochSeconds(pubdate).toUi(),
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = statusKey,
            )
        ),
        accountType = AccountType.Specific(accountKey),
    )
}

private fun BilibiliVideoInfo.toUiTimelineV2(accountKey: MicroBlogKey, playUrl: String? = null): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = bvid, host = "bilibili.com")
    val userKey = MicroBlogKey(id = ownerMid.toString(), host = "bilibili.com")
    val media = if (pic.isNotEmpty()) {
        if (playUrl != null) {
            listOf(UiMedia.Video(url = playUrl, thumbnailUrl = pic, description = title, width = 16f, height = 9f, customHeaders = persistentHashMapOf("Referer" to "https://www.bilibili.com")))
        } else {
            listOf(UiMedia.Image(url = pic, previewUrl = pic, description = title, width = 0f, height = 0f, sensitive = false))
        }
    } else emptyList()

    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = ownerName, host = "bilibili.com"),
        avatar = UiMedia.Image(url = ownerFace, previewUrl = ownerFace, description = ownerName, width = 0f, height = 0f, sensitive = false),
        nameInternal = ownerName.toUiPlainText(),
        platformType = PlatformType.Bilibili,
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null, description = null, matrices = UiProfile.Matrices(0, 0, 0), mark = persistentListOf(), bottomContent = null,
    )

    val card = UiCard(media = media.firstOrNull(), title = title, description = description, url = "https://www.bilibili.com/video/$bvid")

    return UiTimelineV2.Post(
        platformType = PlatformType.Bilibili,
        images = media.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(original = title.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = card,
        createdAt = Instant.fromEpochSeconds(pubdate).toUi(),
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = statusKey,
            )
        ),
        accountType = AccountType.Specific(accountKey),
    )
}

private fun BilibiliUser.toUiProfile(accountKey: MicroBlogKey): UiProfile = UiProfile(
    key = MicroBlogKey(id = mid.toString(), host = "bilibili.com"),
    handle = UiHandle(raw = name, host = "bilibili.com"),
    avatar = UiMedia.Image(url = face, previewUrl = face, description = name, width = 0f, height = 0f, sensitive = false),
    nameInternal = name.toUiPlainText(),
    platformType = PlatformType.Bilibili,
    clickEvent = ClickEvent.Deeplink(
        dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
            accountType = AccountType.Specific(accountKey),
            userKey = MicroBlogKey(id = mid.toString(), host = "bilibili.com"),
        )
    ),
    banner = null, description = sign.toUiPlainText(), matrices = UiProfile.Matrices(0, 0, 0), mark = persistentListOf(), bottomContent = null,
)

// ==================== 数据加载器 ====================

internal class BilibiliLoader(
    val accountKey: MicroBlogKey,
    private val service: BilibiliService,
) : UserLoader,
    PostLoader,
    RelationLoader,
    NotificationLoader {

    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val mid = uiHandle.normalizedRaw.toLongOrNull() ?: error("Invalid Bilibili UID")
        return userById(mid.toString())
    }

    override suspend fun userById(id: String): UiProfile {
        val mid = id.toLongOrNull() ?: error("Invalid Bilibili UID: $id")
        val spaceInfo = service.getSpaceInfo(mid) ?: error("User not found: $id")
        val user = BilibiliUser.fromJson(spaceInfo) ?: error("Failed to parse user: $id")
        return user.toUiProfile(accountKey)
    }

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val videoInfo = service.getVideoInfo(statusKey.id) ?: error("Video not found: ${statusKey.id}")
        val info = BilibiliVideoInfo.fromJson(videoInfo) ?: error("Failed to parse video: ${statusKey.id}")
        val playUrl = if (info.cid > 0) {
            service.getPlayUrlLegacy(bvid = info.bvid, cid = info.cid)
        } else null
        println("BilibiliLoader.status: bvid=${info.bvid}, cid=${info.cid}, playUrl=${playUrl?.take(60)}...")
        return info.toUiTimelineV2(accountKey, playUrl = playUrl)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) { }

    override suspend fun relation(userKey: MicroBlogKey): dev.dimension.flare.ui.model.UiRelation =
        dev.dimension.flare.ui.model.UiRelation(following = false)

    override suspend fun follow(userKey: MicroBlogKey) {
        service.modifyRelation(userKey.id.toLongOrNull() ?: return, 1)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.modifyRelation(userKey.id.toLongOrNull() ?: return, 2)
    }

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Bilibili block is not supported")
    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Bilibili unblock is not supported")
    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Bilibili mute is not supported")
    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Bilibili unmute is not supported")

    override suspend fun notificationBadgeCount(): Int {
        val data = service.getFeedUnread() ?: return 0
        val reply = data["reply"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val at = data["at"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val like = data["like"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        return reply + at + like
    }

    
}

// ==================== 首页推荐流 ====================

internal class BilibiliHomeRemoteLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_home_${accountKey.id}"
    override val supportPrepend: Boolean = false
    private var currentIdx: Int = 0

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val idx = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        if (request is PagingRequest.Refresh) currentIdx = 0
        val items = service.getRecommendedFeed(idx = idx, refreshCount = pageSize)
        val data = items.mapNotNull { BilibiliFeedItem.fromJson(it) }.map { it.toUiTimelineV2(accountKey) }
        currentIdx = idx + data.size
        return PagingResult(data = data, endOfPaginationReached = data.isEmpty(), nextKey = currentIdx.toString())
    }
}

// ==================== 热门视频 ====================

// ==================== 排行榜 ====================

internal class BilibiliRankingRemoteLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_ranking_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val items = service.getRankingVideos()
        val data = items.mapNotNull { BilibiliFeedItem.fromJson(it) }.map { it.toUiTimelineV2(accountKey) }
        return PagingResult(data = data, endOfPaginationReached = true)
    }
}

// ==================== 评论 ====================

// ==================== 搜索 ====================

// ==================== 关注动态流 ====================

// ==================== 用户主页 ====================

internal class BilibiliUserVideosLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
    private val mid: Long,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_user_videos_${mid}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 1
        val items = service.getSpaceVideos(mid = mid, page = page, pageSize = pageSize)
        val data = items.mapNotNull { BilibiliFeedItem.fromJson(it) }.map { it.toUiTimelineV2(accountKey) }
        return PagingResult(data = data, endOfPaginationReached = data.isEmpty(), nextKey = if (data.isNotEmpty()) "${page + 1}" else null)
    }
}

private fun parseDynamicItem(obj: kotlinx.serialization.json.JsonObject, accountKey: MicroBlogKey): UiTimelineV2? = try {
    val modules = obj["modules"]?.jsonObject ?: return null
    val author = modules["module_author"]?.jsonObject ?: return null
    val mid = author["mid"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
    val uname = author["name"]?.jsonPrimitive?.content ?: ""
    val face = (author["face"]?.jsonPrimitive?.content ?: "").replace("http://", "https://")
    val pubTs = author["pub_ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
    val dynamic = modules["module_dynamic"]?.jsonObject
    val major = dynamic?.get("major")?.jsonObject
    val majorType = major?.get("type")?.jsonPrimitive?.content ?: ""
    val desc = dynamic?.get("desc")?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

    val userKey = MicroBlogKey(id = mid.toString(), host = "bilibili.com")
    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = uname, host = "bilibili.com"),
        avatar = if (face.isNotEmpty()) UiMedia.Image(url = face, previewUrl = face, description = uname, width = 0f, height = 0f, sensitive = false) else null,
        nameInternal = uname.toUiPlainText(),
        platformType = PlatformType.Bilibili,
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null, description = null, matrices = UiProfile.Matrices(0, 0, 0), mark = persistentListOf(), bottomContent = null,
    )

    var bvid = ""
    var title = ""
    val media = when (majorType) {
        "ARCHIVE" -> {
            val archive = major?.get("archive")?.jsonObject
            bvid = archive?.get("bvid")?.jsonPrimitive?.content ?: ""
            title = archive?.get("title")?.jsonPrimitive?.content ?: ""
            val p = (archive?.get("cover")?.jsonPrimitive?.content ?: "").replace("http://", "https://")
            if (p.isNotEmpty()) listOf(UiMedia.Image(url = p, previewUrl = p, description = title, width = 0f, height = 0f, sensitive = false)) else emptyList()
        }
        "DRAW" -> {
            val draw = major?.get("draw")?.jsonObject
            val items = draw?.get("items")?.jsonArray
            val images = items?.mapNotNull { it.jsonObject["src"]?.jsonPrimitive?.content?.replace("http://", "https://") } ?: emptyList()
            images.map { UiMedia.Image(url = it, previewUrl = it, description = "", width = 0f, height = 0f, sensitive = false) }
        }
        else -> emptyList()
    }

    val statusKey = MicroBlogKey(id = obj["id_str"]?.jsonPrimitive?.content ?: return null, host = "bilibili.com")
    val card = if (bvid.isNotEmpty()) UiCard(media = media.firstOrNull(), title = title, description = null, url = "https://www.bilibili.com/video/$bvid") else null
    val contentText = if (desc.isNotEmpty()) desc else title

    UiTimelineV2.Post(
        platformType = PlatformType.Bilibili,
        images = media.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(original = contentText.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = card,
        createdAt = Instant.fromEpochSeconds(pubTs).toUi(),
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(id = bvid, host = "bilibili.com"),
            )
        ),
        accountType = AccountType.Specific(accountKey),
    )
} catch (_: Exception) { null }

internal class BilibiliDynamicRemoteLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_dynamic_$accountKey"
    override val supportPrepend: Boolean = false
    private var currentOffset: String = ""

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey ?: ""
        if (request is PagingRequest.Refresh) currentOffset = ""
        val data = service.getDynamicFeed(offset = offset.ifEmpty { currentOffset })
        val items = data?.get("items")?.jsonArray?.mapNotNull { it.jsonObject.let { parseDynamicItem(it, accountKey) } } ?: emptyList()
        val newOffset = data?.get("offset")?.jsonPrimitive?.content ?: ""
        val hasMore = data?.get("has_more")?.jsonPrimitive?.content?.toBoolean() ?: false
        if (newOffset.isNotEmpty()) currentOffset = newOffset
        return PagingResult(
            data = items,
            endOfPaginationReached = !hasMore,
            nextKey = if (hasMore) newOffset else null,
        )
    }
}

internal class BilibiliSearchLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_search_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 1
        val data = service.searchAll(keyword = query, page = page, pageSize = pageSize) ?: return PagingResult(endOfPaginationReached = true)
        val results = data["result"]?.jsonArray ?: return PagingResult(endOfPaginationReached = true)
        val items = results.mapNotNull { item ->
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "video") {
                BilibiliFeedItem.fromJson(obj)
            } else null
        }.map { it.toUiTimelineV2(accountKey) }
        val end = data["numPages"]?.jsonPrimitive?.content?.toIntOrNull()?.let { page >= it } ?: true
        return PagingResult(
            data = items,
            endOfPaginationReached = end,
            nextKey = if (!end) "${page + 1}" else null,
        )
    }
}

internal class BilibiliSearchUserLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "bilibili_search_user_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 1
        val data = service.searchAll(keyword = query, page = page, pageSize = pageSize) ?: return PagingResult(endOfPaginationReached = true)
        val results = data["result"]?.jsonArray ?: return PagingResult(endOfPaginationReached = true)
        val items = results.mapNotNull { item ->
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "bili_user") {
                BilibiliUser.fromJson(obj)
            } else null
        }.map { it.toUiProfile(accountKey) }
        val end = data["numPages"]?.jsonPrimitive?.content?.toIntOrNull()?.let { page >= it } ?: true
        return PagingResult(
            data = items,
            endOfPaginationReached = end,
            nextKey = if (!end) "${page + 1}" else null,
        )
    }
}

internal data class BilibiliComment(
    val rpid: Long, val content: String, val uname: String, val mid: Long,
    val avatar: String, val like: Int, val ctime: Long, val rcount: Int,
) {
    companion object {
        fun fromJson(obj: kotlinx.serialization.json.JsonObject): BilibiliComment? = try {
            val member = obj["member"]?.jsonObject ?: return null
            BilibiliComment(
                rpid = obj["rpid"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null,
                content = obj["content"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "",
                uname = member["uname"]?.jsonPrimitive?.content ?: "",
                mid = member["mid"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                avatar = (member["avatar"]?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
                like = obj["like"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                ctime = obj["ctime"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                rcount = obj["rcount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        } catch (_: Exception) { null }
    }
}

private fun BilibiliComment.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = rpid.toString(), host = "bilibili.com")
    val userKey = MicroBlogKey(id = mid.toString(), host = "bilibili.com")
    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = uname, host = "bilibili.com"),
        avatar = if (avatar.isNotEmpty()) {
            UiMedia.Image(url = avatar, previewUrl = avatar, description = uname, width = 0f, height = 0f, sensitive = false)
        } else null,
        nameInternal = uname.toUiPlainText(),
        platformType = PlatformType.Bilibili,
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null, description = null, matrices = UiProfile.Matrices(0, 0, 0), mark = persistentListOf(), bottomContent = null,
    )
    return UiTimelineV2.Post(
        platformType = PlatformType.Bilibili,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(original = content.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = Instant.fromEpochSeconds(ctime).toUi(),
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = statusKey,
            )
        ),
        accountType = AccountType.Specific(accountKey),
    )
}

internal class BilibiliCommentsLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
    private val oid: Long = 0,
    private val bvid: String = "",
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_comments_${if (bvid.isNotEmpty()) bvid else oid}_$accountKey"
    override val supportPrepend: Boolean = false
    private var resolvedOid: Long = oid

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        if (resolvedOid == 0L && bvid.isNotEmpty()) {
            val videoInfo = service.getVideoInfo(bvid)
            resolvedOid = videoInfo?.get("aid")?.jsonPrimitive?.content?.toLongOrNull() ?: return PagingResult(endOfPaginationReached = true)
        }
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 1
        val data = service.getComments(oid = resolvedOid, page = page, pageSize = pageSize)
        val replies = data?.get("replies")?.jsonArray?.mapNotNull { it.jsonObject.let { BilibiliComment.fromJson(it) } } ?: emptyList()
        val end = data?.get("cursor")?.jsonObject?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
        return PagingResult(
            data = replies.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = end,
            nextKey = if (!end) "${page + 1}" else null,
        )
    }
}

internal class BilibiliPopularRemoteLoader(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_popular_${accountKey.id}"
    override val supportPrepend: Boolean = false
    private var currentPage: Int = 1

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: currentPage
        if (request is PagingRequest.Refresh) currentPage = 1
        val items = service.getPopularVideos(page = page, pageSize = pageSize)
        val data = items.mapNotNull { BilibiliFeedItem.fromJson(it) }.map { it.toUiTimelineV2(accountKey) }
        currentPage = page + 1
        return PagingResult(data = data, endOfPaginationReached = data.isEmpty(), nextKey = currentPage.toString())
    }
}
