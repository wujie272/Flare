package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.cbart.api.CbartContentItem
import dev.dimension.flare.data.network.cbart.api.CbartApiContentType
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

import dev.dimension.flare.data.network.cbart.api.CbartStatsItem

internal fun CbartContentItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {

    val images = listOfNotNull(coverImage).mapNotNull { url ->
        url.toUiImage()
    }.toImmutableList()

    val typeLabel = when (type) {
        CbartApiContentType.Video -> "🎬 Video"
        CbartApiContentType.Picture -> "🖼 Gallery"
        CbartApiContentType.Fiction -> "📖 Fiction"
        CbartApiContentType.Unknown -> ""
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = typeLabel.toUiPlainText(),
        user = UiProfile(
            key = MicroBlogKey(id = uid ?: "", host = CBART_HOST),
            handle = UiHandle(raw = username ?: "Cbart User", host = CBART_HOST),
            avatar = null as UiMedia.Image?,
            nameInternal = (username ?: "Cbart User").toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null as UiMedia.Image?,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = typeLabel.toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = id, host = CBART_HOST),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_$id")
}

internal fun CbartStatsItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val avatar = avatar?.let { url ->
        if (url.isNotBlank()) {
            UiMedia.Image(url = url, previewUrl = url, description = null, height = 0f, width = 0f, sensitive = false)
        } else null
    }

    val displayName = nickName ?: username ?: "Unknown"

    val description = buildString {
        append("⭐ Rank #${rank ?: "?"}")
        if (!followerNum.isNullOrBlank()) append(" · ${followerNum} Followers")
        if (!totalPost.isNullOrBlank()) append(" · ${totalPost} Posts")
        if (!totalEarned.isNullOrBlank()) append(" · Earned: $${totalEarned}")
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = uid, host = CBART_HOST),
            handle = UiHandle(raw = username ?: uid, host = CBART_HOST),
            avatar = avatar,
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null as UiMedia.Image?,
            description = description.toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = description.toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = uid, host = CBART_HOST),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_$uid",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_$uid")
}
