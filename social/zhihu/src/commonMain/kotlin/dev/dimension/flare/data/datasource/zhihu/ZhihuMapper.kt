package dev.dimension.flare.data.datasource.zhihu

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.zhihu.ZhihuNotificationItem
import dev.dimension.flare.data.network.zhihu.ZhihuPerson
import dev.dimension.flare.data.network.zhihu.ZhihuDailyStory
import dev.dimension.flare.data.network.zhihu.ZhihuFeedItem
import dev.dimension.flare.data.network.zhihu.ZhihuHotItem
import dev.dimension.flare.data.platform.ZHIHU_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.zhihuVoteUp
import dev.dimension.flare.ui.model.mapper.zhihuBookmark
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

// ========== ZhihuPerson → UiProfile ==========

/**
 * 知乎用户资料 → UiProfile
 */
internal fun ZhihuPerson.toUiProfile(
    accountKey: MicroBlogKey?,
): UiProfile {
    val userKey = MicroBlogKey(id = id, host = ZHIHU_HOST)
    return UiProfile(
        key = userKey,
        handle = UiHandle(raw = name, host = ZHIHU_HOST),
        avatar = avatarUrl?.let {
            UiMedia.Image(url = it, previewUrl = it, description = name, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = name.toUiPlainText(),
        platformType = PlatformType.Zhihu,
        clickEvent = ClickEvent.Deeplink(url = "https://www.zhihu.com/people/${urlToken ?: id}"),
        banner = null,
        description = headline?.toUiPlainText(),
        matrices = UiProfile.Matrices(
            answerCount.toLong(),
            followerCount.toLong(),
            articlesCount.toLong(),
        ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

// ========== ZhihuNotificationItem → UiTimelineV2 ==========

/**
 * 知乎通知条目 → UiTimelineV2
 */
internal fun ZhihuNotificationItem.toNotificationUiTimelineItem(
    accountKey: MicroBlogKey,
): UiTimelineV2 {
    val contentText = buildString {
        actorName?.let { append(it) }
        verb.takeIf { it.isNotBlank() }?.let { append(" $it") }
        targetTitle?.let { append("《$it》") }
        appendLine()
        if (targetText != null && targetText != targetTitle) {
            append(targetText)
        }
    }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = "zhihu_notification", host = ZHIHU_HOST),
            handle = UiHandle(raw = "知乎通知", host = ZHIHU_HOST),
            avatar = null,
            nameInternal = "知乎通知".toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = contentText.toUiPlainText(),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = "notification_$id", host = ZHIHU_HOST),
        card = targetLink?.takeIf { it.isNotBlank() }?.let {
            UiCard(
                media = null,
                title = targetTitle ?: "",
                description = targetText ?: "",
                url = "https://www.zhihu.com$it",
            )
        },
        createdAt = Instant.fromEpochMilliseconds(createTime * 1000).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            url = targetLink?.let { "https://www.zhihu.com$it" } ?: "https://www.zhihu.com/notifications",
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_notification_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_notification_$id")
}
internal fun ZhihuHotItem.toUiTimelineItem(
    accountKey: MicroBlogKey,
    rank: Int,
): UiTimelineV2 {
    val webUrl = url.takeIf { it.startsWith("http") } 
        ?: "https://www.zhihu.com/question/$id/"
    val contentText = buildString {
        append("#$rank  $title")
        appendLine()
        append("🔥 ${formatHotValue(hotValue)}")
        if (answerCount > 0) append(" · ${answerCount}个回答")
    }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = "zhihu_hot", host = ZHIHU_HOST),
            handle = UiHandle(raw = "知乎热榜", host = ZHIHU_HOST),
            avatar = null,
            nameInternal = "知乎热榜".toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = contentText.toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = contentText.toUiPlainText(),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id, host = ZHIHU_HOST),
        card = UiCard(
            media = null,
            title = title,
            description = "🔥 ${formatHotValue(hotValue)} · ${answerCount}个回答",
            url = webUrl,
        ),
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = webUrl),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_hot_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_hot_$id")
}

/**
 * 知乎日报条目 → UiTimelineV2
 */
internal fun ZhihuDailyStory.toUiTimelineItem(
    accountKey: MicroBlogKey,
): UiTimelineV2 {
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = "zhihu_daily", host = ZHIHU_HOST),
            handle = UiHandle(raw = "知乎日报", host = ZHIHU_HOST),
            avatar = imageUrl?.let { UiMedia.Image(url = it, previewUrl = it, description = title, height = 0f, width = 0f, sensitive = false) },
            nameInternal = "知乎日报".toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = (hint ?: "").toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = title.toUiPlainText(),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id, host = ZHIHU_HOST),
        card = if (imageUrl != null) {
            UiCard(
                media = UiMedia.Image(url = imageUrl, previewUrl = imageUrl, description = title, height = 0f, width = 0f, sensitive = false),
                title = title,
                description = hint ?: "",
                url = url,
            )
        } else null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = url),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_daily_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_daily_$id")
}

/**
 * 推荐流/搜索结果条目 → UiTimelineV2
 * 带点赞和收藏按钮
 */
internal fun ZhihuFeedItem.toUiTimelineItem(
    accountKey: MicroBlogKey,
): UiTimelineV2 {
    val authorName = authorName ?: "匿名用户"
    val statusKey = MicroBlogKey(id = id, host = ZHIHU_HOST)
    val contentText = buildString {
        append(title)
        appendLine()
        if (excerpt.isNotBlank()) {
            append(excerpt.take(200))
        }
    }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = id, host = ZHIHU_HOST),
            handle = UiHandle(raw = authorName, host = ZHIHU_HOST),
            avatar = authorAvatar?.let { 
                UiMedia.Image(url = it, previewUrl = it, description = authorName, height = 0f, width = 0f, sensitive = false) 
            },
            nameInternal = authorName.toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = (if (type == "answer") "回答" else "文章").toUiPlainText(),
            matrices = UiProfile.Matrices(voteCount.toLong(), 0, commentCount.toLong()),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = contentText.toUiPlainText(),
        actions = persistentListOf(
            ActionMenu.zhihuVoteUp(
                statusKey = statusKey,
                voted = false,
                count = voteCount.toLong(),
                accountKey = accountKey,
            ),
            ActionMenu.zhihuBookmark(
                statusKey = statusKey,
                bookmarked = false,
                accountKey = accountKey,
            ),
        ),
        poll = null,
        statusKey = statusKey,
        card = UiCard(
            media = null,
            title = title,
            description = "$authorName · 👍 $voteCount · 💬 $commentCount",
            url = url,
        ),
        createdAt = if (createdAt > 0) Instant.fromEpochMilliseconds(createdAt * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = url),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_${type}_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_${type}_$id")
}

private fun formatHotValue(value: String): String {
    val num = value.toLongOrNull() ?: return value
    val tenThousand = 10_000L
    val hundredMillion = 100_000_000L
    return when {
        num >= hundredMillion -> "${(num.toDouble() / hundredMillion.toDouble() * 10).toLong() / 10.0}亿"
        num >= tenThousand -> "${(num.toDouble() / tenThousand.toDouble() * 10).toLong() / 10.0}万"
        else -> num.toString()
    }
}
