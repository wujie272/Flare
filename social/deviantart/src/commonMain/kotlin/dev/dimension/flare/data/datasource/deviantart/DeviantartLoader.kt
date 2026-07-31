package dev.dimension.flare.data.datasource.deviantart

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.deviantart.DeviantartService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class DeviantartLoader(
    val accountKey: MicroBlogKey,
    private val service: DeviantartService,
) : UserLoader,
    PostLoader,
    RelationLoader,
    NotificationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val profile = service.userProfile(uiHandle.normalizedRaw)
            ?: error("User not found: ${uiHandle.normalizedRaw}")
        return profile.toUiProfile(accountKey)
    }

    override suspend fun userById(id: String): UiProfile {
        // DeviantArt uses username as ID
        val profile = service.userProfile(id) ?: error("User not found: $id")
        return profile.toUiProfile(accountKey)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val profile = service.userProfile(userKey.id)
        return UiRelation(
            following = profile?.isWatching ?: false,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.watchUser(userKey.id)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unwatchUser(userKey.id)
    }

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("DeviantArt block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("DeviantArt unblock is not supported")

    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("DeviantArt mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("DeviantArt unmute is not supported")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val detail = service.deviationDetail(statusKey.id)
            ?: error("Deviation not found: ${statusKey.id}")
        return detail.toUiDetailItem(accountKey)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("DeviantArt post deletion is not supported")

    override suspend fun notificationBadgeCount(): Int = 0
}
