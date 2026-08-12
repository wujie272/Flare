package dev.dimension.flare.data.datasource.deviantart

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.network.deviantart.DeviantartDeviation
import dev.dimension.flare.data.network.deviantart.DeviantartDeviationDetail
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

private const val DA_HOST = "deviantart.com"

internal fun DeviantartDeviation.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = deviationId, host = DA_HOST)
    val userKey = MicroBlogKey(id = artistName, host = DA_HOST)

    val media = listOfNotNull(
        contentUrl?.let {
            UiMedia.Image(
                url = it,
                previewUrl = previewUrl ?: thumbnailUrl ?: it,
                width = 0f,
                height = 0f,
                description = title,
                sensitive = false,
            )
        } ?: previewUrl?.let {
            UiMedia.Image(
                url = it,
                previewUrl = thumbnailUrl ?: it,
                width = 0f,
                height = 0f,
                description = title,
                sensitive = false,
            )
        },
    )

    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = "$artistName@deviantart.com", host = DA_HOST),
        avatar = artistAvatar?.let {
            UiMedia.Image(url = it, previewUrl = it, description = artistName, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = artistName.toUiPlainText(),
        platformId = "Deviantart",
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null,
        description = null,
        matrices = UiProfile.Matrices(0, 0, 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

    val contentText = buildString {
        append(title)
        if (description != null && description.isNotBlank()) {
            appendLine()
            append(description)
        }
    }

    val post = UiTimelineV2.Post(
        platformId = "Deviantart",
        images = media.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(
            ActionMenu.Item(
                icon = dev.dimension.flare.ui.model.UiIcon.Heart,
                text = ActionMenu.Item.Text.Raw("${stats?.favourites ?: 0}"),
                count = dev.dimension.flare.ui.model.UiNumber(stats?.favourites ?: 0),
                clickEvent = ClickEvent.event(
                    accountKey,
                    PostEvent.Deviantart.Favourite(
                        postKey = statusKey,
                        favourited = isFavourite,
                        accountKey = accountKey,
                    ),
                ),
                color = if (isFavourite) ActionMenu.Item.Color.Red else null,
            ),
        ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = if (published > 0) Instant.fromEpochMilliseconds(published * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Gallery.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = statusKey,
            )
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
        accountType = AccountType.Specific(accountKey),
        itemKey = "da_$deviationId",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "da_$deviationId")
}

internal fun DeviantartDeviationDetail.toUiDetailItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = deviationId, host = DA_HOST)
    val userKey = MicroBlogKey(id = artistName, host = DA_HOST)

    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = "$artistName@deviantart.com", host = DA_HOST),
        avatar = artistAvatar?.let {
            UiMedia.Image(url = it, previewUrl = it, description = artistName, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = artistName.toUiPlainText(),
        platformId = "Deviantart",
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null,
        description = null,
        matrices = UiProfile.Matrices(0, 0, 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

    val post = UiTimelineV2.Post(
        platformId = "Deviantart",
        images = listOfNotNull(
            contentUrl?.let {
                UiMedia.Image(
                    url = it,
                    previewUrl = previewUrl ?: thumbnailUrl ?: it,
                    width = 0f,
                    height = 0f,
                    description = title,
                    sensitive = false,
                )
            } ?: previewUrl?.let {
                UiMedia.Image(
                    url = it,
                    previewUrl = thumbnailUrl ?: it,
                    width = 0f,
                    height = 0f,
                    description = title,
                    sensitive = false,
                )
            },
        ).toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(
            (description ?: title).toUiPlainText()
        ),
        actions = persistentListOf(
            ActionMenu.Item(
                icon = dev.dimension.flare.ui.model.UiIcon.Heart,
                text = ActionMenu.Item.Text.Raw("${stats?.favourites ?: 0}"),
                count = dev.dimension.flare.ui.model.UiNumber(stats?.favourites ?: 0),
                clickEvent = ClickEvent.Noop,
            ),
        ),
        poll = null,
        statusKey = statusKey,
        card = UiCard(
            media = thumbnailUrl?.let {
                UiMedia.Image(url = it, previewUrl = it, description = title, height = 0f, width = 0f, sensitive = false)
            },
            title = title,
            description = category,
            url = "https://www.deviantart.com/$artistName/art/$deviationId",
        ),
        createdAt = if (published > 0) Instant.fromEpochMilliseconds(published * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "da_detail_$deviationId",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "da_detail_$deviationId")
}

internal fun dev.dimension.flare.data.network.deviantart.DeviantartUserProfile.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val userKey = MicroBlogKey(id = userName, host = DA_HOST)
    return UiProfile(
        key = userKey,
        handle = UiHandle(raw = "$userName@deviantart.com", host = DA_HOST),
        avatar = avatarUrl?.let {
            UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = userName.toUiPlainText(),
        platformId = "Deviantart",
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = coverUrl?.let {
            UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false)
        },
        description = buildString {
            tagline?.let { append(it) }
            artistLevel?.let {
                if (tagline != null) append("\n")
                append("Artist Level: $it")
            }
        }.takeIf { it.isNotBlank() }?.toUiPlainText(),
        matrices = UiProfile.Matrices(
            followsCount = friendsCount,
            fansCount = watchersCount,
            statusesCount = favouritesCount,
        ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal fun dev.dimension.flare.data.network.deviantart.DeviantartComment.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = commentId, host = "deviantart.com")
    val post = UiTimelineV2.Post(
        platformId = "Deviantart",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = userName, host = "deviantart.com"),
            handle = UiHandle(raw = "$userName@deviantart.com", host = "deviantart.com"),
            avatar = userAvatar?.let {
                UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false)
            },
            nameInternal = userName.toUiPlainText(),
            platformId = "Deviantart",
            clickEvent = ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = MicroBlogKey(id = userName, host = "deviantart.com"),
                )
            ),
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(body.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = if (posted > 0) Instant.fromEpochMilliseconds(posted * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null, visibility = null, replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "da_comment_$commentId",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "da_comment_$commentId")
}

internal fun dev.dimension.flare.data.network.deviantart.DeviantartNotification.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val contentText = buildString {
        fromUsername?.let { append("$it ") }
        append(subject)
        if (body.isNotBlank()) { appendLine(); append(body) }
    }
    val post = UiTimelineV2.Post(
        platformId = "Deviantart",
        images = persistentListOf(),
        sensitive = false, contentWarning = null,
        user = null,
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(), poll = null,
        statusKey = MicroBlogKey(id = "da_notif_$id", host = "deviantart.com"),
        card = null,
        createdAt = if (ts > 0) Instant.fromEpochMilliseconds(ts * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "da_notif_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "da_notif_$id")
}

internal fun dev.dimension.flare.data.network.deviantart.DeviantartUser.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val userKey = MicroBlogKey(id = userName, host = DA_HOST)
    return UiProfile(
        key = userKey,
        handle = UiHandle(raw = "$userName@deviantart.com", host = DA_HOST),
        avatar = avatarUrl?.let {
            UiMedia.Image(url = it, previewUrl = it, description = userName, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = userName.toUiPlainText(),
        platformId = "Deviantart",
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            )
        ),
        banner = null,
        description = null,
        matrices = UiProfile.Matrices(0, 0, 0),
        mark = persistentListOf(),
        bottomContent = null,
    )
}
