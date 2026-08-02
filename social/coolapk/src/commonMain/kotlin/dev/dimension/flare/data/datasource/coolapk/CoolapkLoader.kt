package dev.dimension.flare.data.datasource.coolapk
import kotlinx.serialization.json.JsonObject

import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.coolapk.CoolapkNotificationItem
import dev.dimension.flare.data.network.coolapk.CoolapkFeedItem
import dev.dimension.flare.data.network.coolapk.CoolapkService
import dev.dimension.flare.data.network.coolapk.CoolapkUser
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.json.Json

internal class CoolapkLoader(
    val accountKey: MicroBlogKey,
    private val service: CoolapkService,
) : UserLoader,
    RelationLoader,
    NotificationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = emptySet()

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== UserLoader ====================

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val uid = uiHandle.raw
        return loadUser(uid)
    }

    override suspend fun userById(id: String): UiProfile = loadUser(id)

    private suspend fun loadUser(uid: String): UiProfile {
        println("[CoolapkLoader] loadUser start: uid=$uid")
        val jsonObj = service.fetchUserProfile(uid) ?: error("User not found: $uid")
        println("[CoolapkLoader] jsonObj keys: ${jsonObj.keys}")
        val user = try {
            json.decodeFromJsonElement(CoolapkUser.serializer(), jsonObj)
        } catch (e: Exception) {
            println("[CoolapkLoader] deserialize error: ${e.message}")
            e.printStackTrace()
            throw e
        }
        println("[CoolapkLoader] render start")
        return user.render(accountKey)
    }

    // ==================== RelationLoader ====================

    // ==================== PostLoader ====================

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val jsonObj = service.fetchFeedDetail(id = statusKey.id) ?: error("Status not found: $statusKey")
        val item = json.decodeFromJsonElement(CoolapkFeedItem.serializer(), jsonObj)
        return item.render(accountKey)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        throw UnsupportedOperationException("Coolapk delete status is not supported yet")
    }

    // ==================== RelationLoader ====================

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun follow(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Coolapk follow is not supported yet")

    override suspend fun unfollow(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Coolapk unfollow is not supported yet")

    override suspend fun block(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Coolapk block is not supported yet")

    override suspend fun unblock(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Coolapk unblock is not supported yet")

    override suspend fun mute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Coolapk mute is not supported yet")

    override suspend fun unmute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Coolapk unmute is not supported yet")

    // ==================== NotificationLoader ====================

    override suspend fun notificationBadgeCount(): Int = notificationBadgeCounts().values.sum()

    /** 获取各类型通知的未读数 */
    suspend fun notificationBadgeCounts(): Map<NotificationFilter, Int> {
        // 拉取任意通知列表，notifyCount 附带在返回的 item 中
        val items = service.fetchNotificationList(type = "atme", page = 1)
        if (items.isEmpty()) return emptyMap()

        return try {
            val firstItem = json.decodeFromJsonElement(CoolapkNotificationItem.serializer(), items.first())
            val nc = firstItem.notifyCount ?: return emptyMap()
            mapOf(
                NotificationFilter.Mention to (nc.atme + nc.atcommentme),
                NotificationFilter.Comment to nc.commentme,
                NotificationFilter.Like to nc.feedlike,
                NotificationFilter.All to (nc.atme + nc.atcommentme + nc.commentme + nc.feedlike + nc.contactsFollow + nc.message),
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
