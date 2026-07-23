package dev.dimension.flare.data.datasource.wordpress

import dev.dimension.flare.data.network.wordpress.WPPost
import dev.dimension.flare.data.network.wordpress.WPCategory
import dev.dimension.flare.data.platform.WORDPRESS_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

/**
 * WPPost → UiTimelineV2
 */
internal fun WPPost.toUiTimelineItem(
    accountKey: MicroBlogKey,
    siteName: String,
): UiTimelineV2 {
    val postUrl = link ?: ""
    val postTitle = title?.rendered ?: ""
    val rawContent = content?.rendered ?: ""
    
    // 摘要：优先用 excerpt，没有则从内容中提取首段文本
    val excerptText = excerpt?.rendered?.let { stripHtml(it).take(200) }
        ?: extractFirstText(rawContent).take(200)

    // 封面图：优先用 featured_media，没有则从内容中提取第一张图片
    val coverUrl = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
        ?: embedded?.featuredMedia?.firstOrNull()?.mediaDetails?.sizes?.get("medium")?.sourceUrl
        ?: embedded?.featuredMedia?.firstOrNull()?.mediaDetails?.sizes?.get("thumbnail")?.sourceUrl
        ?: extractFirstImage(rawContent)

    // 从 _embedded 中提取作者
    val authorName = embedded?.author?.firstOrNull()?.name
    val authorAvatar = embedded?.author?.firstOrNull()?.avatarUrls?.entries
        ?.minByOrNull { it.key.removePrefix("https://").removePrefix("http://").substringBefore("/").toIntOrNull() ?: Int.MAX_VALUE }
        ?.value

    // 从 _embedded 中提取分类
    val categoryNames = embedded?.terms
        ?.flatten()
        ?.filter { it.taxonomy == "category" }
        ?.mapNotNull { it.name }
        ?.take(3)
        ?: emptyList()

    val contentText = buildString {
        append(postTitle)
        if (categoryNames.isNotEmpty()) {
            append("\n🏷️ ${categoryNames.joinToString(" · ")}")
        }
        if (excerptText.isNotBlank()) {
            append("\n$excerptText")
        }
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.WordPress,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = siteName, host = WORDPRESS_HOST),
            handle = UiHandle(raw = authorName ?: siteName, host = WORDPRESS_HOST),
            avatar = authorAvatar?.toUiImage(),
            nameInternal = (authorName ?: siteName).toUiPlainText(),
            platformType = PlatformType.WordPress,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = WORDPRESS_HOST),
        card = if (coverUrl != null || postTitle.isNotBlank()) {
            UiCard(
                media = coverUrl?.toUiImage(),
                title = postTitle,
                description = excerptText,
                url = postUrl,
            )
        } else null,
        createdAt = parseWpDate(dateGmt ?: date ?: ""),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = postUrl),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "wp_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "wp_${id}")
}

/**
 * WPCategory → UiTimelineV2（用于分类 Tab）
 */
internal fun WPCategory.toUiTimelineItem(
    accountKey: MicroBlogKey,
    siteName: String,
): UiTimelineV2 {
    val contentText = buildString {
        append(name ?: slug ?: "")
        if (description != null && description.isNotBlank()) {
            append("\n$description")
        }
        if (count != null && count > 0) {
            append("\n📄 $count 篇文章")
        }
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.WordPress,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = id.toString(), host = WORDPRESS_HOST),
            handle = UiHandle(raw = name ?: slug ?: "", host = WORDPRESS_HOST),
            avatar = null,
            nameInternal = (name ?: slug ?: "Category").toUiPlainText(),
            platformType = PlatformType.WordPress,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = "cat_$id", host = WORDPRESS_HOST),
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
        itemKey = "wp_cat_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "wp_cat_$id")
}

/**
 * 解析 WordPress 日期格式
 * "2026-07-23T12:00:46" → Instant
 */
private fun parseWpDate(dateStr: String): UiDateTime {
    val instant = try {
        val iso = dateStr.replace(" ", "T").let {
            if (!it.endsWith("Z") && !it.contains("+")) "${it}Z" else it
        }
        Instant.parse(iso)
    } catch (_: Exception) {
        Instant.fromEpochMilliseconds(0)
    }
    return instant.toUi()
}

/**
 * 从 HTML 内容中提取第一张图片 URL
 */
private fun extractFirstImage(html: String): String? {
    // 匹配 <img> 标签中的 src 属性
    val regex = Regex("""<img[^>]+src="([^"]+)""")
    return regex.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

/**
 * 从 HTML 内容中提取首段文本
 */
private fun extractFirstText(html: String): String {
    // 先尝试匹配 <p> 标签中的文本
    val pRegex = Regex("""<p[^>]*>(.*?)</p>""")
    val pMatch = pRegex.find(html)
    if (pMatch != null) {
        val text = stripHtml(pMatch.groupValues[1])
        if (text.isNotBlank()) return text
    }
    // 兜底：去掉所有 HTML 标签后取前 200 字
    return stripHtml(html).take(200)
}

/**
 * 简单的 HTML 标签清理
 */
private fun stripHtml(html: String): String {
    return html
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .trim()
}

/**
 * 字符串转 UiMedia.Image
 */
private fun String.toUiImage(): UiMedia.Image = UiMedia.Image(
    url = this,
    previewUrl = this,
    description = "",
    height = 0f,
    width = 0f,
    sensitive = false,
)
