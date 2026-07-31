package dev.dimension.flare.data.datasource.zhihu

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.zhihu.ZhihuNotificationItem
import dev.dimension.flare.data.network.zhihu.ZhihuPerson
import dev.dimension.flare.data.network.zhihu.ZhihuDailyStory
import dev.dimension.flare.data.network.zhihu.ZhihuFeedItem
import dev.dimension.flare.data.network.zhihu.ZhihuHotItem
import dev.dimension.flare.data.network.zhihu.ZhihuComment
import dev.dimension.flare.data.network.zhihu.ZhihuVideoInfo
import dev.dimension.flare.data.network.zhihu.ZhihuMomentsItem
import dev.dimension.flare.data.platform.ZHIHU_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.mapper.zhihuVoteUp
import dev.dimension.flare.ui.model.mapper.zhihuReply
import dev.dimension.flare.ui.model.mapper.zhihuBookmark
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ========== ZhihuPerson → UiProfile ==========

// ========== 详情页（回答/文章正文） ==========

/**
 * 将 API 返回的 JSON 详情转换为 UiTimelineV2
 * 支持回答和文章两种类型
 */

/**
 * 预处理知乎正文 HTML
 */
private fun String.cleanZhihuHtml(): String {
    var result = this
    result = result.replace(Regex("<noscript>.*?</noscript>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("""<img\s[^>]*data-original="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)) { match ->
        val original = match.groupValues[1]
        match.value.replace(Regex("""src="[^"]*""""), """src="$original"""") 
    }
    return result
}

/**
 * 将 API 返回的 JSON 详情转换为 UiTimelineV2
 * 支持回答和文章两种类型
 */
internal fun JsonObject.toDetailUiTimelineItem(
    accountKey: MicroBlogKey,
    statusKey: MicroBlogKey,
    videoInfo: ZhihuVideoInfo? = null,
): UiTimelineV2 {
    val rawId = this["id"]?.jsonPrimitive?.content ?: ""
    val content = this["content"]?.jsonPrimitive?.content ?: ""
    val voteupCount = this["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val commentCount = this["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

    // 解析反应状态
    val reaction = this["reaction"]?.jsonObject
    val relation = reaction?.get("relation")?.jsonObject
    val voted = relation?.get("vote")?.jsonPrimitive?.content == "UP"

    // 解析作者
    val authorObj = this["author"]?.jsonObject
    val authorName = authorObj?.get("name")?.jsonPrimitive?.content ?: ""
    val authorId = authorObj?.get("id")?.jsonPrimitive?.content ?: ""
    val authorUrlToken = authorObj?.get("url_token")?.jsonPrimitive?.content ?: authorId
    val authorAvatar = authorObj?.get("avatar_url")?.jsonPrimitive?.content
    val authorHeadline = authorObj?.get("headline")?.jsonPrimitive?.content

    // 解析问题信息（仅回答）
    val questionObj = this["question"]?.jsonObject
    val questionTitle = questionObj?.get("title")?.jsonPrimitive?.content
    val questionId = questionObj?.get("id")?.jsonPrimitive?.content

    // 解析时间
    val createdTime = this["created_time"]?.jsonPrimitive?.content?.toLongOrNull()
        ?: this["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    val updatedTime = this["updated_time"]?.jsonPrimitive?.content?.toLongOrNull()
        ?: this["updated"]?.jsonPrimitive?.content?.toLongOrNull()

    val userKey = MicroBlogKey(id = authorUrlToken, host = ZHIHU_HOST)

    val user = UiProfile(
        key = userKey,
        handle = UiHandle(raw = "$authorName@www.zhihu.com", host = ZHIHU_HOST),
        avatar = authorAvatar?.let {
            UiMedia.Image(url = it, previewUrl = it, description = authorName, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = authorName.toUiPlainText(),
        platformType = PlatformType.Zhihu,
        clickEvent = ClickEvent.Deeplink(
            dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = MicroBlogKey(id = authorUrlToken, host = ZHIHU_HOST),
            )
        ),
        banner = null,
        description = authorHeadline?.toUiPlainText(),
        matrices = UiProfile.Matrices(0, 0, 0),
        mark = persistentListOf(),
        bottomContent = null,
    )

    // 构建正文
    val contentText = buildString {
        if (questionTitle != null) {
            appendLine(questionTitle)
            appendLine()
        }
        append(content)
    }

    // 构建卡片信息
    val card = questionTitle?.let {
        UiCard(
            media = null,
            title = questionTitle,
            description = updatedTime?.let { "编辑于 ${formatDetailTimestamp(it)}" } ?: "",
            url = if (questionId != null) {
                "https://www.zhihu.com/question/$questionId/answer/$rawId"
            } else {
                "https://zhuanlan.zhihu.com/p/$rawId"
            },
        )
    }

    // 构建视频附件
    val mediaImages = videoInfo?.let {
        if (it.url != null) {
            val attachment = this["attachment"]?.jsonObject
            val thumbnail = attachment?.get("video")?.jsonObject
                ?.get("videoInfo")?.jsonObject
                ?.get("thumbnail")?.jsonPrimitive?.content
            persistentListOf(
                UiMedia.Video(
                    url = it.url,
                    thumbnailUrl = thumbnail ?: "",
                    width = it.width.toFloat(),
                    height = it.height.toFloat().coerceAtLeast(1f),
                    description = null,
                )
            )
        } else null
    } ?: persistentListOf()

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = mediaImages,
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(parseHtml(contentText.cleanZhihuHtml()).toUi()),
        actions = persistentListOf(
            ActionMenu.zhihuReply(
                statusKey = statusKey,
                count = commentCount.toLong(),
                accountKey = accountKey,
            ),
            ActionMenu.zhihuVoteUp(
                statusKey = statusKey,
                voted = voted,
                count = voteupCount.toLong(),
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
        card = card,
        createdAt = Instant.fromEpochMilliseconds(createdTime * 1000).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_detail_${statusKey.id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_detail_${statusKey.id}")
}

private fun formatDetailTimestamp(seconds: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(seconds * 1000)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${dateTime.year}-${(dateTime.month.ordinal + 1).toString().padStart(2, '0')}-${(dateTime.day).toString().padStart(2, '0')} " +
        "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

/**
 * 知乎用户资料 → UiProfile
 */
internal fun ZhihuPerson.toUiProfile(
    accountKey: MicroBlogKey?,
    /** 强制使用指定的 ID 作为 userKey（而非 urlToken），解决 UserHandler 缓存 key 不匹配问题 */
    forceId: String? = null,
): UiProfile {
    val userKey = MicroBlogKey(id = forceId ?: urlToken ?: id, host = ZHIHU_HOST)
    return UiProfile(
        key = userKey,
        handle = UiHandle(raw = name, host = ZHIHU_HOST),
        avatar = avatarUrl?.let {
            UiMedia.Image(url = it, previewUrl = it, description = name, height = 0f, width = 0f, sensitive = false)
        },
        nameInternal = name.toUiPlainText(),
        platformType = PlatformType.Zhihu,
        clickEvent = if (accountKey != null) {
            ClickEvent.Deeplink(
                dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = MicroBlogKey(id = urlToken ?: id, host = ZHIHU_HOST),
                )
            )
        } else {
            ClickEvent.Deeplink(url = "https://www.zhihu.com/people/${urlToken ?: id}")
        },
        banner = null,
        description = headline?.toUiPlainText(),
        matrices = UiProfile.Matrices(
            followerCount.toLong(),
            followingCount.toLong(),
            answerCount.toLong(),
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
    // 根据通知类型选择图标
    val icon = when (notificationCategory) {
        "comment" -> dev.dimension.flare.ui.model.UiIcon.Comment
        "follow" -> dev.dimension.flare.ui.model.UiIcon.Follow
        "vote" -> dev.dimension.flare.ui.model.UiIcon.Like
        else -> dev.dimension.flare.ui.model.UiIcon.Notification
    }
    val message = UiTimelineV2.Message(
        user = null,
        icon = icon,
        type = UiTimelineV2.Message.Type.Raw(contentText),
        statusKey = MicroBlogKey(id = "notification_$id", host = ZHIHU_HOST),
        createdAt = Instant.fromEpochMilliseconds(createTime * 1000).toUi(),
        clickEvent = ClickEvent.Deeplink(
            url = targetLink?.let { "https://www.zhihu.com$it" } ?: "https://www.zhihu.com/notifications",
        ),
        accountType = AccountType.Specific(accountKey),
    )
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText(contentText.toUiPlainText()),
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
    return UiTimelineV2.TimelinePostItem(
        post = post,
        presentation = UiTimelineV2.PostPresentation(message = message),
        itemKey = "zhihu_notification_$id",
    )
}
internal fun ZhihuHotItem.toUiTimelineItem(
    accountKey: MicroBlogKey,
    rank: Int,
): UiTimelineV2 {
    val webUrl = url.takeIf { it.startsWith("http") } 
        ?: "https://www.zhihu.com/question/$id/"
    val hotLabel = hotValue.takeIf { it.isNotBlank() && it != "0" } ?: "热榜"
    val thumbnailMedia = thumbnail?.let {
        UiMedia.Image(url = it, previewUrl = it, description = title, height = 0f, width = 0f, sensitive = false)
    }
    val contentText = buildString {
        append("#$rank  $title")
        appendLine()
        append("🔥 $hotLabel")
        if (answerCount > 0) append(" · ${answerCount}个回答")
    }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = "zhihu_hot", host = ZHIHU_HOST),
            handle = UiHandle(raw = "hot", host = ZHIHU_HOST),
            avatar = thumbnailMedia,
            nameInternal = "知乎热榜".toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = contentText.toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id, host = ZHIHU_HOST),
        card = UiCard(
            media = thumbnailMedia,
            title = title,
            description = "🔥 $hotLabel · ${answerCount}个回答",
            url = webUrl,
        ),
        createdAt = if (createdAt > 0) Instant.fromEpochMilliseconds(createdAt * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
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
    val dailyEpoch = parseDailyDate(date)
    // 从 hint 中提取作者名，格式如 "野狸子 · 9 分钟阅读"
    val authorName = hint?.substringBefore(" · ")?.takeIf { it.isNotBlank() } ?: "知乎日报"
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = "zhihu_daily", host = ZHIHU_HOST),
            handle = UiHandle(raw = "daily", host = ZHIHU_HOST),
            avatar = imageUrl?.let { UiMedia.Image(url = it, previewUrl = it, description = title, height = 0f, width = 0f, sensitive = false) },
            nameInternal = authorName.toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = (hint ?: "").toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = UiTranslatableText(title.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id, host = ZHIHU_HOST),
        card = if (imageUrl != null) {
            UiCard(
                media = UiMedia.Image(url = imageUrl, previewUrl = imageUrl, description = title, height = 0f, width = 0f, sensitive = false),
                title = title,
                description = hint ?: "",
                url = originalUrl ?: url,
            )
        } else null,
        createdAt = Instant.fromEpochMilliseconds(dailyEpoch).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = originalUrl ?: url),
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
            val plainText = excerpt.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
            append(plainText.take(300))
        }
    }
    val pictures = if (type == "video" && videoCover != null) {
        if (videoPlayUrl != null) {
            persistentListOf(
                UiMedia.Video(
                    url = videoPlayUrl,
                    thumbnailUrl = videoCover,
                    width = videoWidth.toFloat(),
                    height = videoHeight.toFloat().coerceAtLeast(1f),
                    description = title,
                )
            )
        } else {
            persistentListOf(
                UiMedia.Image(
                    url = videoCover,
                    previewUrl = videoCover,
                    description = title,
                    width = 0f,
                    height = 0f,
                    sensitive = false,
                )
            )
        }
    } else {
        // 从 excerpt 或 feed thumbnail 中提取首张图片
        val firstImage = thumbnail ?: kotlin.runCatching {
            val imgRegex = Regex("""<img\s[^>]*src="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
            imgRegex.find(excerpt)?.groupValues?.get(1)
        }.getOrNull()
        if (firstImage != null) {
            persistentListOf(
                UiMedia.Image(
                    url = firstImage,
                    previewUrl = firstImage,
                    description = title,
                    width = 0f,
                    height = 0f,
                    sensitive = false,
                )
            )
        } else {
            persistentListOf()
        }
    }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = pictures,
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = authorId ?: id, host = ZHIHU_HOST),
            handle = UiHandle(raw = authorName, host = ZHIHU_HOST),
            avatar = authorAvatar?.let { 
                UiMedia.Image(url = it, previewUrl = it, description = authorName, height = 0f, width = 0f, sensitive = false) 
            },
            nameInternal = authorName.toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = if (authorId != null) {
                ClickEvent.Deeplink(
                    dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = MicroBlogKey(id = authorId, host = ZHIHU_HOST),
                    )
                )
            } else ClickEvent.Noop,
            banner = null,
            description = when (type) {
                "answer" -> "回答".toUiPlainText()
                "article" -> "文章".toUiPlainText()
                "video" -> "视频".toUiPlainText()
                "pin" -> "想法".toUiPlainText()
                "question" -> "问题".toUiPlainText()
                else -> "".toUiPlainText()
            },
            matrices = UiProfile.Matrices(voteCount.toLong(), 0, commentCount.toLong()),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(
            ActionMenu.zhihuReply(
                statusKey = statusKey,
                count = commentCount.toLong(),
                accountKey = accountKey,
            ),
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
        card = null,
        createdAt = if (createdAt > 0) Instant.fromEpochMilliseconds(createdAt * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = statusKey,
            )
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_${type}_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_${type}_$id")
}

internal fun ZhihuComment.toUiTimelineItem(
    accountKey: MicroBlogKey,
): UiTimelineV2 {
    val statusKey = MicroBlogKey(id = id, host = ZHIHU_HOST)
    val replyTo = replyToAuthorName?.let { "回复 @$it" }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = authorId ?: id, host = ZHIHU_HOST),
            handle = UiHandle(raw = authorName ?: "匿名用户", host = ZHIHU_HOST),
            avatar = authorAvatar?.let {
                UiMedia.Image(url = it, previewUrl = it, description = authorName ?: "", height = 0f, width = 0f, sensitive = false)
            },
            nameInternal = (authorName ?: "匿名用户").toUiPlainText(),
            platformType = PlatformType.Zhihu,
            clickEvent = if (authorId != null) {
                ClickEvent.Deeplink(
                    dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = MicroBlogKey(id = authorId, host = ZHIHU_HOST),
                    )
                )
            } else ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = UiTranslatableText(content.toUiPlainText()),
        actions = persistentListOf(
            ActionMenu.zhihuVoteUp(
                statusKey = statusKey,
                voted = false,
                count = likeCount.toLong(),
                accountKey = accountKey,
            ),
        ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = if (createdAt > 0) Instant.fromEpochMilliseconds(createdAt * 1000).toUi() else Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = replyTo,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_comment_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "zhihu_comment_$id")
}

/**
 * 解析日报日期字符串（yyyyMMdd）为本地时区午夜的时间戳（毫秒）
 * 使用 kotlinx-datetime 的 LocalDate 确保时区正确处理，
 * 避免手动计算闰年/世纪年导致的 Bug。
 */

/**
 * 关注动态条目 -> UiTimelineV2
 */
internal fun ZhihuMomentsItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val actionText = when {
        verb.contains("VOTEUP") || verb.contains("VOTE_PIN") -> "赞了"
        verb.contains("ANSWER_QUESTION") -> "回答了"
        verb.contains("CREATE_ARTICLE") -> "发表了文章"
        verb.contains("CREATE_PIN") -> "发布了想法"
        verb.contains("FOLLOW") -> "关注了"
        else -> ""
    }
    val contentText = buildString {
        if (actorName.isNotBlank()) append(actorName)
        if (actionText.isNotBlank()) append(" $actionText")
        if (targetType.isNotBlank()) {
            append(" ")
            when (targetType) {
                "answer" -> append("回答")
                "article" -> append("文章")
                "question" -> append("问题")
                "pin" -> append("想法")
                else -> append(targetType)
            }
        }
        appendLine()
        if (title.isNotBlank()) { append("「$title」"); appendLine() }
        if (excerpt.isNotBlank()) append(excerpt.take(200))
    }
    val icon = when {
        verb.contains("VOTEUP") || verb.contains("VOTE_PIN") -> UiIcon.Like
        verb.contains("ANSWER") || verb.contains("CREATE") -> UiIcon.Comment
        verb.contains("FOLLOW") -> UiIcon.Follow
        else -> UiIcon.Notification
    }
    val message = UiTimelineV2.Message(
        user = null, icon = icon,
        type = UiTimelineV2.Message.Type.Raw(contentText),
        statusKey = MicroBlogKey(id = "moment_${targetId}_$createdAt", host = ZHIHU_HOST),
        createdAt = Instant.fromEpochMilliseconds(createdAt * 1000).toUi(),
        clickEvent = ClickEvent.Deeplink(url = url),
        accountType = AccountType.Specific(accountKey),
    )
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Zhihu,
        images = persistentListOf(), sensitive = false, contentWarning = null, user = null,
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(), poll = null,
        statusKey = MicroBlogKey(id = "moment_${targetId}_$createdAt", host = ZHIHU_HOST),
        card = if (url.isNotBlank()) UiCard(media = null, title = title, description = excerpt.takeIf { it.isNotBlank() } ?: actionText, url = url) else null,
        createdAt = Instant.fromEpochMilliseconds(createdAt * 1000).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = url),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "zhihu_moment_${targetId}_$createdAt",
    )
    return UiTimelineV2.TimelinePostItem(post = post, presentation = UiTimelineV2.PostPresentation(message = message), itemKey = "zhihu_moment_${targetId}_$createdAt")
}

private fun parseDailyDate(date: String): Long {

    if (date.length != 8) return 0L
    val year = date.substring(0, 4).toIntOrNull() ?: return 0L
    val month = date.substring(4, 6).toIntOrNull() ?: return 0L
    val day = date.substring(6, 8).toIntOrNull() ?: return 0L
    return LocalDate(year, month, day)
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}
