package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.network.cbart.api.*
import dev.dimension.flare.data.network.cbart.api.LzjFuliData
import dev.dimension.flare.data.network.cbart.api.LzjVideoItem
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent

import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.model.mapper.cbartFavourite
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.data.datasource.microblog.PostEvent
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val LZJ_CDN = "https://www.tpzf001.com"

/**
 * 妖狐吧每日福利 -> UiTimelineV2
 */
internal fun LzjFuliData.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val contentText = buildString {
        append("🎁 每日福利\n\n")
        bonus?.let { append("💰 金币: +$it\n") }
        diamond?.let { append("💎 钻石: +$it\n") }
        message?.let { append("\n$it") }
    }
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText(contentText.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = "fuli_daily", host = CBART_HOST),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Gallery.Detail(
                statusKey = MicroBlogKey(id = "fuli_daily", host = CBART_HOST),
                accountType = AccountType.Specific(accountKey),
            ),
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_fuli_daily",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_fuli_daily")
}

/**
 * 妖狐吧文章 -> UiTimelineV2
 */
internal fun LzjArticleItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = listOfNotNull(image_path ?: image).mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$LZJ_CDN$url"
        fullUrl.toUiImage()
    }.toImmutableList()

    val displayName = cn_name ?: username ?: "妖狐吧"
    val subtitle = buildString {
        tag?.let { if (it.isNotBlank()) append("🏷️$it ") }
        views?.let { if (it > 0) append("👁️$it ") }
        replyNum?.let { if (it > 0) append("💬$it ") }
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = false,
        contentWarning = UiTranslatableText("📢 公告".toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = displayName, host = CBART_HOST),
            avatar = null,
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(buildString {
            if (subtitle.isNotBlank()) append(subtitle).append("\n")
            append(content_short ?: title ?: "")
        }.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_article_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_article_${id}")
}

/**
 * 妖狐吧视频 -> UiTimelineV2
 */
internal fun LzjVideoItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = images?.mapNotNull { img ->
        val url = img.path ?: return@mapNotNull null
        val fullUrl = if (url.startsWith("http")) url else "$LZJ_CDN$url"
        fullUrl.toUiImage()
    }.orEmpty().toImmutableList()

    val ownerName = owner?.nickName ?: owner?.displayName ?: owner?.username ?: uid?.toString() ?: "妖狐"
    val avatarUrl = owner?.avatarUrl ?: owner?.avatar?.let { "$LZJ_CDN$it" }

    val contentWarning = buildString {
        append("🎬 视频")
        if (priceDiamond != null && priceDiamond > 0) append(" 💎$priceDiamond")
        if (extraText2 != null) append(" 📦$extraText2")
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = UiTranslatableText(contentWarning.toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = ownerName, host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = ownerName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(buildString {
            append(title ?: "")
            if (favNum != null && favNum > 0) append("\n❤️ $favNum 收藏")
            if (isOriginal == 1) append(" 原创")
        }.toUiPlainText()),
        actions = persistentListOf(
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
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Gallery.Detail(
                statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
                accountType = AccountType.Specific(accountKey),
            ),
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_video_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_video_${id}")
}

/**
 * 妖狐吧图片分组 -> UiTimelineV2
 */
internal fun LzjPictureGroup.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = images?.mapNotNull { img ->
        val url = img.path ?: return@mapNotNull null
        val fullUrl = if (url.startsWith("http")) url else "$LZJ_CDN$url"
        fullUrl.toUiImage()
    }.orEmpty().toImmutableList()

    val ownerName = owner?.nickName ?: owner?.displayName ?: owner?.username ?: uid?.toString() ?: "妖狐"
    val avatarUrl = owner?.avatarUrl ?: owner?.avatar?.let { "$LZJ_CDN$it" }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = true,
        contentWarning = UiTranslatableText("🖼 图集".toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = ownerName, host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = ownerName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(buildString {
            append(title ?: "")
            if (fav_num != null && fav_num > 0) append("\n❤️ $fav_num 收藏")
        }.toUiPlainText()),
        actions = persistentListOf(
            ActionMenu.cbartFavourite(
                statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
                favourited = false,
                count = (fav_num ?: 0).toLong(),
                accountKey = accountKey,
            ),
        ),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_pic_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_pic_${id}")
}

/**
 * 妖狐吧作者 -> UiTimelineV2（工作室/作者卡片）
 */
internal fun LzjProducerItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val avatarFullUrl = avatarUrl ?: avatar?.let { "$LZJ_CDN$it" }
    val displayName = name ?: "作者 #$id"

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = UiTranslatableText("🏪 作者".toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: id.toString(), host = CBART_HOST),
            handle = UiHandle(raw = displayName, host = CBART_HOST),
            avatar = avatarFullUrl?.toUiImage(),
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = description?.toUiPlainText(),
            matrices = UiProfile.Matrices(
                fansCount = (followerNum ?: 0).toLong(),
                followsCount = 0, statusesCount = 0,
            ),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(buildString {
            contentNum?.let { append("📦 $it 内容") }
        }.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id.toString(), host = CBART_HOST),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_producer_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_producer_${id}")
}

/**
 * 妖狐吧评论 -> UiTimelineV2
 */
internal fun LzjCommentItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val senderName = nickName ?: username ?: "用户 #$uid"
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = senderName, host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = senderName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText((content ?: "").toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = "comment_${id}", host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_comment_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_comment_${id}")
}

/**
 * 妖狐吧消息 -> UiTimelineV2（通知）
 */
internal fun LzjMessageItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val senderName = nickName ?: username ?: "用户 #$uid"
    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = senderName, host = CBART_HOST),
            avatar = null,
            nameInternal = senderName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(("💬 $senderName: ${content ?: title ?: ""}").toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = "msg_$uid", host = CBART_HOST),
        card = null,
        createdAt = (postTime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_msg_$uid",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_msg_$uid")
}

/**
 * 妖狐吧视频 -> GalleryDetail（视频详情页）
 */
internal fun LzjVideoItem.toGalleryDetail(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
): GalleryDetail {
    val imageItems = this@toGalleryDetail.images?.mapNotNull { img ->
        val url = img.path ?: return@mapNotNull null
        val fullUrl = if (url.startsWith("http")) url else "$LZJ_CDN$url"
        UiMedia.Image(
            url = fullUrl,
            previewUrl = img.mPath?.let { if (it.startsWith("http")) it else "$LZJ_CDN$it" } ?: fullUrl,
            description = title,
            height = (img.imageHeight ?: 0).toFloat(),
            width = (img.imageWidth ?: 0).toFloat(),
            sensitive = true,
        )
    }.orEmpty().toImmutableList()

    val ownerName = owner?.nickName ?: owner?.displayName ?: owner?.username ?: uid?.toString() ?: ""
    val avatarFullUrl = owner?.avatarUrl ?: owner?.avatar?.let { "$LZJ_CDN$it" }

    val author = if (ownerName.isNotBlank()) {
        UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = ownerName, host = CBART_HOST),
            avatar = avatarFullUrl?.toUiImage(),
            nameInternal = ownerName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        )
    } else null

    val contentText = buildString {
        content?.let { if (it.isNotBlank()) append(it).append("\n") }
        if (priceDiamond != null && priceDiamond > 0) append("💎 $priceDiamond\n")
        if (purchasedNum != null && purchasedNum.isNotBlank()) append("🛒 $purchasedNum 已购买\n")
        if (extraText2 != null) append("📦 $extraText2")
    }.takeIf { it.isNotBlank() }

    val matrix = buildList {
        if (playerShowedNum != null && playerShowedNum > 0) {
            add(GalleryDetail.Matrix(icon = UiIcon.Eye, count = playerShowedNum.toLong()))
        }
        if (favNum != null && favNum > 0) {
            add(GalleryDetail.Matrix(icon = UiIcon.Favourite, count = favNum.toLong()))
        }
    }.toImmutableList()

    return GalleryDetail(
        orientation = GalleryOrientation.Vertical,
        statusKey = statusKey,
        accountType = AccountType.Specific(accountKey),
        url = "https://shenmatk.com/video/detail?id=${statusKey.id}",
        images = imageItems,
        title = title ?: "",
        author = author,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        content = contentText?.toUiPlainText(),
        isBookmarked = isFav == 1,
        bookmarkAction = ClickEvent.event(
            accountKey = accountKey,
            postEvent = PostEvent.Cbart.Favourite(
                postKey = statusKey,
                favourited = isFav == 1,
                count = (favNum ?: 0).toLong(),
                accountKey = accountKey,
            ),
        ),
        matrix = matrix,
    )
}

/**
 * 尝试解析 "2026-07-09 23:19:16" 格式的时间戳
 */
internal fun tryParseDate(dateStr: String): Instant? {
    return try {
        val iso = dateStr.replace(" ", "T") + "Z"
        Instant.parse(iso)
    } catch (_: Exception) { null }
}
