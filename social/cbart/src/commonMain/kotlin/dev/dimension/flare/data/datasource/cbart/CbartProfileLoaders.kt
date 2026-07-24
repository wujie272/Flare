package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.*
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf

internal class CbartLoader(
    private val service: CbartService,
) : UserLoader, RelationLoader, PostLoader, NotificationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf()

    override suspend fun notificationBadgeCount(): Int = 0

    override suspend fun follow(userKey: MicroBlogKey) {
        // 妖狐吧暂不支持关注操作
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        // 妖狐吧暂不支持取消关注操作
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        throw UnsupportedOperationException("妖狐吧不支持通过 handle 查找用户")

    override suspend fun userById(id: String): UiProfile {
        return UiProfile(
            key = MicroBlogKey(id = id, host = "cbart.net"),
            handle = UiHandle(raw = "$id@cbart.net", host = "cbart.net"),
            avatar = null,
            nameInternal = "妖狐用户 $id".toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        )
    }

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("妖狐吧不支持屏蔽操作")

    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("妖狐吧不支持取消屏蔽操作")

    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("妖狐吧不支持静音操作")

    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("妖狐吧不支持取消静音操作")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        throw UnsupportedOperationException("妖狐吧不支持单独查看状态详情")

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("妖狐吧不支持删除状态")
}
