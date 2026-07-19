package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.cbart.api.CbartArticleItem
import dev.dimension.flare.data.network.cbart.api.CbartContentItem
import dev.dimension.flare.data.network.cbart.api.CbartBlogItem
import dev.dimension.flare.data.network.cbart.api.CbartVideoItem
import dev.dimension.flare.data.network.cbart.api.CbartStudioItem
import dev.dimension.flare.data.network.cbart.api.CbartMessageItem
import dev.dimension.flare.data.network.cbart.api.CbartApiContentType
import dev.dimension.flare.data.network.cbart.api.CbartNewContentItem
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
import dev.dimension.flare.ui.model.mapper.cbartFavourite
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val CBART_CDN = "https://www.tpzf001.com"

/**
 * 从 content_list API 的 CbartContentItem 映射到 UiTimelineV2
 */
internal fun CbartContentItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val coverUrl = path ?: images?.firstOrNull()?.path
    val images = listOfNotNull(coverUrl).mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$CBART_CDN$url"
        fullUrl.toUiImage()
    }.toImmutableList()

    val typeLabel = when {
        extraText1?.contains(":") == true -> "🎬 Video"
        images.isNotEmpty() -> "🖼 Gallery"
        else -> ""
    }

    val subtitle = buildString {
        if (priceDiamond != null && priceDiamond > 0) append("💎$priceDiamond ")
        if (isOriginal == 1) append("原创 ")
        if (favNum != null && favNum > 0) append("❤️$favNum ")
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = typeLabel.toUiPlainText(),
        user = null,
        content = (title ?: "").toUiPlainText(),
        actions = persistentListOf<ActionMenu>(
            ActionMenu.cbartFavourite(
                statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
                favourited = false,
                count = (favNum ?: 0).toLong(),
                accountKey = accountKey,
            ),
        ),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = "https://www.linzijiang.app/video/detail?id=$id"),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_${id}")
}

/**
 * 从 blog_list API 的 CbartBlogItem 映射到 UiTimelineV2
 */
internal fun CbartBlogItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = coverPicture?.mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$CBART_CDN$url"
        fullUrl.toUiImage()
    }.orEmpty().toImmutableList()

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = "📝 Blog".toUiPlainText(),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = uid?.toString() ?: "", host = CBART_HOST),
            avatar = null,
            nameInternal = (uid?.toString() ?: "Cbart").toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = (title ?: "").toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_blog_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_blog_${id}")
}

/**
 * 从 studio_list API 的 CbartStudioItem 映射到 UiTimelineV2
 * 展示工作室封面、名称、关注者数
 */
internal fun CbartArticleItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = listOfNotNull(image_path ?: image).mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$CBART_CDN$url"
        fullUrl.toUiImage()
    }.toImmutableList()

    // 显示名：cn_name > username > uid
    val displayName = cn_name ?: username ?: "Cbart"
    // 生成占位头像（基于名字首字母，颜色由名字哈希决定）
    val avatarUrl = buildAvatarUrl(displayName)

    // 构建底部信息栏（浏览量、回复数、标签）
    val subtitle = buildString {
        if (tag != null && tag.isNotBlank()) append("🏷️$tag ")
        if (views != null && views > 0) append("👁️$views ")
        if (replyNum != null && replyNum > 0) append("💬$replyNum ")
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = false,
        contentWarning = "📢 ${cn_name ?: "公告"}".toUiPlainText(),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = username ?: "", host = CBART_HOST),
            avatar = avatarUrl.toUiImage(),
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
                )
            ),
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = buildString {
            if (subtitle.isNotBlank()) append(subtitle).append("\n")
            append(content_short ?: title ?: "")
        }.toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_article_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_article_${id}")
}

/**
 * 生成基于名字首字母的占位头像 URL
 * 使用 ui-avatars.com 服务，颜色基于名字哈希固定
 */
private fun buildAvatarUrl(name: String): String {
    val encoded = name.take(2).let {
        // 简单的 URL 编码：只替换空格和特殊字符
        it.replace(" ", "%20").replace("&", "%26").replace("?", "%3F")
    }
    return "https://ui-avatars.com/api/?name=$encoded&background=6B4F8E&color=fff&bold=true&size=64"
}

internal fun CbartStudioItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val coverUrl = coverPictureUrl ?: coverPicture
    val images = listOfNotNull(coverUrl).mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$CBART_CDN$url"
        fullUrl.toUiImage()
    }.toImmutableList()

    val ownerName = owner?.nickName ?: owner?.username ?: name ?: "Studio #$id"
    val descriptionText = description ?: ""
    val supporterInfo = if (supporterNum != null && supporterNum > 0) "👥 $supporterNum 订阅" else ""

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = "🏪 Studio".toUiPlainText(),
        user = UiProfile(
            key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
            handle = UiHandle(raw = ownerName, host = CBART_HOST),
            avatar = owner?.avatarUrl?.toUiImage(),
            nameInternal = ownerName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = descriptionText.toUiPlainText(),
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = (supporterInfo).toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (updatetime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_studio_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_studio_${id}")
}

/**
 * 从 video_list API 的 CbartVideoItem 映射到 UiTimelineV2
 */
internal fun CbartVideoItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = images?.mapNotNull { img ->
        val fullUrl = if (img.path.startsWith("http")) img.path else "$CBART_CDN${img.path}"
        fullUrl.toUiImage()
    }.orEmpty().toImmutableList()

    // 优先用 API 返回的 owner 字段，其次用缓存兜底，最后 fallback 到 uid
    val ownerNickName = owner?.nickName ?: owner?.displayName ?: owner?.username
    val displayName = ownerNickName ?: uid?.toString() ?: "Cbart"
    val avatarUrl = owner?.avatarUrl ?: owner?.avatar?.let { "$CBART_CDN$it" }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = buildString {
            append("🎬 Video")
            if (priceDiamond != null && priceDiamond > 0) append(" 💎$priceDiamond")
            if (extraText2 != null) append(" 📦$extraText2")
        }.toUiPlainText(),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = displayName, host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = buildString {
            append(title ?: "")
            if (favNum != null && favNum > 0) append("\n❤️ $favNum 收藏")
            if (isOriginal == 1) append(" 原创")
        }.toUiPlainText(),
        actions = persistentListOf<ActionMenu>(
            ActionMenu.cbartFavourite(
                statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
                favourited = isFav == 1,
                count = (favNum ?: 0).toLong(),
                accountKey = accountKey,
            ),
        ),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(url = "https://www.linzijiang.app/video/detail?id=$id"),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_video_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_video_${id}")
}

/**
 * 从 message_user_list API 的 CbartMessageItem 映射到 UiTimelineV2（通知）
 */
internal fun CbartMessageItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val senderName = nickName ?: username ?: "User #$uid"
    val messageContent = content ?: title ?: ""
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
            handle = UiHandle(raw = senderName, host = CBART_HOST),
            avatar = user?.avatarUrl?.toUiImage(),
            nameInternal = senderName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = ("💬 $senderName: $messageContent").toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = "msg_$uid", host = CBART_HOST),
        card = null,
        createdAt = (postTime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_msg_$uid",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_msg_$uid")
}

/**
 * 从 /api/get_new_content 的 CbartNewContentItem 映射到 UiTimelineV2
 */
internal fun CbartNewContentItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = listOfNotNull(coverPicture).mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$CBART_CDN$url"
        fullUrl.toUiImage()
    }.toImmutableList()

    val typeLabel = when (contentType) {
        "video" -> "🎬 Video"
        "album" -> "🖼 Gallery"
        "fiction" -> "📖 Fiction"
        else -> "📄 Post"
    }

    // 优先用 API 返回的 owner 字段，其次用 username，最后 fallback 到 uid
    val ownerNickName = owner?.nickName ?: owner?.displayName ?: owner?.username
    val displayName = ownerNickName ?: username ?: uid?.toString() ?: "Cbart"
    val avatarUrl = owner?.avatarUrl ?: owner?.avatar?.let { "$CBART_CDN$it" }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = typeLabel.toUiPlainText(),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = displayName, host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        ),
        content = (title ?: "").toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = MicroBlogKey(id = contentId.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = when (contentType) {
            "video" -> ClickEvent.Deeplink(url = "https://www.linzijiang.app/video/detail?id=$contentId")
            else -> ClickEvent.Noop
        },
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "cbart_new_content_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "cbart_new_content_${id}")
}

/**
 * 尝试解析 "2026-07-09 23:19:16" 格式的时间戳
 * 转换为 ISO-8601 格式后用 Instant.parse()
 */


/**
 * 从 CbartStudioItem 映射到 UiProfile（用于关注列表展示）
 */
internal fun CbartStudioItem.toUiProfile(): UiProfile {
    val ownerName = owner?.nickName ?: owner?.username ?: name ?: "Studio #$id"
    val avatarUrl = owner?.avatarUrl ?: owner?.avatar
    return UiProfile(
        key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
        handle = UiHandle(raw = ownerName, host = CBART_HOST),
        avatar = avatarUrl?.toUiImage(),
        nameInternal = ownerName.toUiPlainText(),
        platformType = PlatformType.Cbart,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = (description ?: "").toUiPlainText(),
        matrices = UiProfile.Matrices(
            fansCount = (followerNum ?: 0).toLong(),
            followsCount = 0,
            statusesCount = 0,
        ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}
