package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.network.coolapk.CoolapkFeedItem
import dev.dimension.flare.data.network.coolapk.CoolapkNotificationItem
import dev.dimension.flare.data.network.coolapk.CoolapkUser
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

internal fun CoolapkUser.render(accountKey: MicroBlogKey): UiProfile {
    val userKey = MicroBlogKey(id = uid.toString(), host = accountKey.host)
    return UiProfile(
        key = userKey,
        handle = UiHandle(raw = username, host = accountKey.host),
        avatar =
            if (avatar.isNotEmpty()) {
                UiMedia.Image(
                    url = avatar,
                    previewUrl = avatar,
                    description = username,
                    width = 0f,
                    height = 0f,
                    sensitive = false,
                )
            } else {
                null
            },
        nameInternal = username.toUiPlainText(),
        platformType = PlatformType.Coolapk,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner =
            if (cover.isNotEmpty()) {
                UiMedia.Image(
                    url = cover,
                    previewUrl = cover,
                    description = username,
                    width = 0f,
                    height = 0f,
                    sensitive = false,
                )
            } else {
                null
            },
        description = bio.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = followersCount.toLong(),
                followsCount = followingCount.toLong(),
                statusesCount = feedCount.toLong(),
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal fun CoolapkFeedItem.render(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = id.toString(), host = accountKey.host)
    val userKey = MicroBlogKey(id = uid.toString(), host = accountKey.host)

    val user =
        if (username.isNotEmpty()) {
            UiProfile(
                key = userKey,
                handle = UiHandle(raw = username, host = accountKey.host),
                avatar =
                    if (userAvatar.isNotEmpty()) {
                        UiMedia.Image(
                            url = userAvatar,
                            previewUrl = userAvatar,
                            description = username,
                            width = 0f,
                            height = 0f,
                            sensitive = false,
                        )
                    } else {
                        null
                    },
                nameInternal = username.toUiPlainText(),
                platformType = PlatformType.Coolapk,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Profile.User(
                            accountType = AccountType.Specific(accountKey),
                            userKey = userKey,
                        ),
                    ),
                banner = null,
                description = null,
                matrices = UiProfile.Matrices(0, 0, 0),
                mark = persistentListOf(),
                bottomContent = null,
            )
        } else {
            null
        }

    val images = parseImages(pic, message)

    val card =
        blockInfo?.let { block ->
            UiCard(
                media =
                    if (block.pic.isNotEmpty()) {
                        UiMedia.Image(
                            url = block.pic,
                            previewUrl = block.pic,
                            description = block.title,
                            width = 0f,
                            height = 0f,
                            sensitive = false,
                        )
                    } else {
                        null
                    },
                title = block.title,
                description = block.content,
                url = "",
            )
        }

    val createdAtUi =
        if (createdAt > 0) {
            Instant.fromEpochSeconds(createdAt).toUi()
        } else {
            Instant.fromEpochSeconds(0).toUi()
        }

    return UiTimelineV2.Post(
        platformType = PlatformType.Coolapk,
        images = images,
        sensitive = false,
        contentWarning = null,
        user = user,
        sourceLanguages = persistentListOf<String>(),
        translationDisplayState = TranslationDisplayState.Hidden,
        content = UiTranslatableText(original = message.toUiPlainText()),
        actions =
            persistentListOf(
                ActionMenu.coolapkLike(
                    statusKey = statusKey,
                    liked = isLiked == 1,
                    count = likeCount.toLong(),
                    accountKey = accountKey,
                ),
                ActionMenu.Item(
                    icon = UiIcon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(replyCount.toLong()),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                            ),
                        ),
                    actionFamily = PostActionFamily.Comment,
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = card,
        createdAt = createdAtUi,
        emojiReactions = persistentListOf<UiTimelineV2.Post.EmojiReaction>(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf<UiTimelineV2.Post.Reference>(),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            ),
        accountType = AccountType.Specific(accountKey),
        itemKey = null,
    )
}

/** 通知项渲染为时间线帖子 */
internal fun CoolapkNotificationItem.render(accountKey: MicroBlogKey): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = id.toString(), host = accountKey.host)
    val userKey = MicroBlogKey(id = fromUid.toString(), host = accountKey.host)

    val userProfile =
        if (fromUsername.isNotEmpty()) {
            UiProfile(
                key = userKey,
                handle = UiHandle(raw = fromUsername, host = accountKey.host),
                avatar =
                    if (fromUserAvatar.isNotEmpty()) {
                        UiMedia.Image(
                            url = fromUserAvatar,
                            previewUrl = fromUserAvatar,
                            description = fromUsername,
                            width = 0f,
                            height = 0f,
                            sensitive = false,
                        )
                    } else {
                        null
                    },
                nameInternal = fromUsername.toUiPlainText(),
                platformType = PlatformType.Coolapk,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Profile.User(
                            accountType = AccountType.Specific(accountKey),
                            userKey = userKey,
                        ),
                    ),
                banner = null,
                description = null,
                matrices = UiProfile.Matrices(0, 0, 0),
                mark = persistentListOf(),
                bottomContent = null,
            )
        } else {
            null
        }

    val createdAtUi =
        if (createdAt > 0) {
            Instant.fromEpochSeconds(createdAt).toUi()
        } else {
            Instant.fromEpochSeconds(0).toUi()
        }

    // 移除 note 中的 HTML 标签
    val cleanNote = note.replace(Regex("<[^>]*>"), "")

    return UiTimelineV2.Post(
        platformType = PlatformType.Coolapk,
        images = persistentListOf<UiMedia>(),
        sensitive = false,
        contentWarning = null,
        user = userProfile,
        sourceLanguages = persistentListOf<String>(),
        translationDisplayState = TranslationDisplayState.Hidden,
        content = UiTranslatableText(original = cleanNote.toUiPlainText()),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = createdAtUi,
        accountType = AccountType.Specific(accountKey),
        itemKey = null,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            ),
    )
}
private fun parseImages(
    pic: String,
    description: String,
): ImmutableList<UiMedia> {
    if (pic.isBlank()) return persistentListOf()
    val urls = pic.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return urls
        .map { url ->
            UiMedia.Image(
                url = url,
                previewUrl = url,
                description = description,
                width = 0f,
                height = 0f,
                sensitive = false,
            )
        }.toImmutableList()
}
