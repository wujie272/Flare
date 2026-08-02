package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.*
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.collections.immutable.persistentListOf

internal class CbartLoader(
    private val service: CbartService,
    private val credentialFlow: Flow<CbartCredential>,
) : UserLoader, RelationLoader, PostLoader, NotificationLoader {
    private suspend fun currentCredential(): CbartCredential? = credentialFlow.firstOrNull()
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
        val cred = currentCredential()
        val displayName = cred?.nickName ?: cred?.userName ?: "妖狐用户 $id"
        val avatarUrl = cred?.avatarUrl?.let {
            if (it.startsWith("http")) it else "https://linzijun.app$it"
        }
        return UiProfile(
            key = MicroBlogKey(id = id, host = CBART_HOST),
            handle = UiHandle(raw = "$id@$CBART_HOST", host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = displayName.toUiPlainText(),
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
