package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class CbartLoader(
    private val service: CbartService,
) : UserLoader,
    RelationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = emptySet()

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        throw UnsupportedOperationException("Cbart user lookup by handle is not supported")

    override suspend fun userById(id: String): UiProfile =
        throw UnsupportedOperationException("Cbart user lookup by id is not supported")

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun follow(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart follow is not supported")

    override suspend fun unfollow(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart unfollow is not supported")

    override suspend fun block(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart unblock is not supported")

    override suspend fun mute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart unmute is not supported")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        throw UnsupportedOperationException("Cbart status detail is not supported - use WebView to open content")

    override suspend fun deleteStatus(statusKey: MicroBlogKey) =
        throw UnsupportedOperationException("Cbart post deletion is not supported")
}
