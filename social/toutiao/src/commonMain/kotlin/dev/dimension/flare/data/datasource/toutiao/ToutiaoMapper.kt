package dev.dimension.flare.data.datasource.toutiao

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.toutiao.ToutiaoHotItem
import dev.dimension.flare.data.platform.TOUTIAO_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/**
 * 将今日头条热榜项映射为 UI 时间线条目
 */
internal fun ToutiaoHotItem.toUiTimelineItem(
    accountKey: MicroBlogKey,
    rank: Int,
): UiTimelineV2 {
    val hotValueFormatted = formatHotValue(hotValue)

    val contentText = buildString {
        append("#$rank  $title")
        appendLine()
        append("🔥 $hotValueFormatted")
        label?.let { append(" · $it") }
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Toutiao,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = clusterId, host = TOUTIAO_HOST),
            handle = UiHandle(raw = "今日头条", host = TOUTIAO_HOST),
            avatar = null as UiMedia.Image?,
            nameInternal = "今日头条".toUiPlainText(),
            platformType = PlatformType.Toutiao,
            clickEvent = ClickEvent.Noop,
            banner = null as UiMedia.Image?,
            description = contentText.toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = contentText.toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = clusterId, host = TOUTIAO_HOST),
        card = if (imageUrl != null) {
            UiCard(
                media = UiMedia.Image(url = imageUrl, previewUrl = imageUrl, description = title, height = 0f, width = 0f, sensitive = false),
                title = title,
                description = "🔥 $hotValueFormatted",
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
        itemKey = "tt_hot_$clusterId",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "tt_hot_$clusterId")
}

private fun formatHotValue(value: String): String {
    val num = value.toLongOrNull() ?: return value
    val hundredMillion = 100_000_000L
    val tenThousand = 10_000L
    return when {
        num >= hundredMillion -> {
            val d = num.toDouble() / hundredMillion.toDouble()
            "${(d * 10).toLong() / 10.0}亿"
        }
        num >= tenThousand -> {
            val d = num.toDouble() / tenThousand.toDouble()
            "${(d * 10).toLong() / 10.0}万"
        }
        else -> num.toString()
    }
}
