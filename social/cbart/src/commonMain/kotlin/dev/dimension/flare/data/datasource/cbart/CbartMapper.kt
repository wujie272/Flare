package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.network.cbart.api.*
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

// ==================== 福利 ====================

/**
 * 妖狐吧每日福利 -> UiTimelineV2
 */
internal fun LzjFuliResponse.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
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

// ==================== 文章 ====================

/**
 * 妖狐吧文章 -> UiTimelineV2
 */
internal fun LzjArticleItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val images = listOfNotNull(image ?: imagePath).mapNotNull { url ->
        val fullUrl = if (url.startsWith("http")) url else "$LZJ_CDN$url"
        fullUrl.toUiImage()
    }.toImmutableList()

    val displayName = cnName ?: username ?: "妖狐吧"
    val subtitle = buildString {
        tag?.let { if (it.isNotBlank()) append("🏷️$it ") }
        views?.let { if (it != "0") append("👁️$it ") }
        replyNum?.let { if (it != "0") append("💬$it ") }
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = images,
        sensitive = false,
        contentWarning = UiTranslatableText("📢 公告".toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid ?: "", host = CBART_HOST),
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
            append(title ?: "")
        }.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id?.toString() ?: "", host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_article_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_article_$id")
}

// ==================== 播放列表（替代视频列表） ====================

/**
 * 妖狐吧播放列表 -> UiTimelineV2
 * 注意：video_list.php 已被服务器封锁，改用 playlist.php
 */
internal fun LzjPlaylistItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val ownerName = owner?.username ?: uid ?: "妖狐"
    val avatarUrl = owner?.avatar?.let { if (it.startsWith("http")) it else "$LZJ_CDN$it" }

    val contentWarning = buildString {
        append("🎬 播放列表")
        if (videoNum != null) append(" ($videoNum 视频)")
    }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = true,
        contentWarning = UiTranslatableText(contentWarning.toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid ?: "", host = CBART_HOST),
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
            description?.let { if (it.isNotBlank()) append("\n$it") }
        }.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = id, host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Gallery.Detail(
                statusKey = MicroBlogKey(id = id, host = CBART_HOST),
                accountType = AccountType.Specific(accountKey),
            ),
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_playlist_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_playlist_$id")
}

/**
 * 播放列表 -> GalleryDetail（详情页）
 */
internal fun LzjPlaylistItem.toGalleryDetail(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
): GalleryDetail {
    val ownerName = owner?.username ?: uid ?: ""
    val avatarFullUrl = owner?.avatar?.let { if (it.startsWith("http")) it else "$LZJ_CDN$it" }

    val author = if (ownerName.isNotBlank()) {
        UiProfile(
            key = MicroBlogKey(id = uid ?: "", host = CBART_HOST),
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

    return GalleryDetail(
        orientation = GalleryOrientation.Vertical,
        statusKey = statusKey,
        accountType = AccountType.Specific(accountKey),
        url = "https://linzijun.app/playlist/detail?id=$id",
        images = persistentListOf(),
        title = title ?: "",
        author = author,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        content = description?.toUiPlainText(),
        isBookmarked = false,
        bookmarkAction = ClickEvent.Noop,
        matrix = persistentListOf(),
    )
}

// ==================== 视频列表 (video_list) ====================

internal fun LzjVideoListItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val imageUrls = images.take(4).mapNotNull { img ->
        val url = if (img.path?.startsWith("http") == true) img.path else "$LZJ_CDN${img.path}"
        url.takeIf { it.isNotBlank() }?.toUiImage()
    }
    val ownerName = owner?.displayName ?: owner?.nickName ?: owner?.username ?: "用户 #$uid"
    val ownerAvatar = (owner?.avatarUrl ?: owner?.avatar)?.let {
        if (it.startsWith("http")) it else "$LZJ_CDN$it"
    }
    val priceDesc = priceDesc ?: if ((price ?: 0) > 0) "$price 金币" else if ((priceDiamond ?: 0) > 0) "$priceDiamond 钻石" else "免费"

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = imageUrls.toImmutableList(),
        sensitive = true,
        contentWarning = UiTranslatableText(
            buildString {
                append("🎬 $priceDesc")
                if (durationHr != null) append(" · $durationHr")
                if (hasPreview == 1) append(" · 可预览")
            }.toUiPlainText(),
        ),
        user = UiProfile(
            key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
            handle = UiHandle(raw = ownerName, host = CBART_HOST),
            avatar = ownerAvatar?.toUiImage(),
            nameInternal = ownerName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText((title ?: "").toUiPlainText()),
        actions = persistentListOf(
            ActionMenu.cbartFavourite(
                statusKey = MicroBlogKey(id = "vl_${id?.toString()}", host = CBART_HOST),
                favourited = false,
                count = (favNum?.toLong() ?: 0).coerceAtLeast(0),
                accountKey = accountKey,
            ),
            ActionMenu.Item(
                updateKey = "cbart_more_${id}",
                icon = UiIcon.More,
                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                actionFamily = PostActionFamily.Share,
                clickEvent = ClickEvent.Noop,
            ),
        ),
        poll = null,
        statusKey = MicroBlogKey(id = "vl_${id}", host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Status.Detail(
                statusKey = MicroBlogKey(id = "vl_${id?.toString()}", host = CBART_HOST),
                accountType = AccountType.Specific(accountKey),
            ),
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_video_${id}",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_video_${id}")
}


internal fun LzjVideoListItem.toGalleryDetail(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
): GalleryDetail {
    val author = if (uid != null) {
        UiProfile(
            key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
            handle = UiHandle(raw = "用户 #$uid", host = CBART_HOST),
            avatar = null,
            nameInternal = "用户 #$uid".toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        )
    } else null

    val imageUrls = images.mapNotNull { img ->
        val url = if (img.path?.startsWith("http") == true) img.path else "$LZJ_CDN${img.path}"
        url.takeIf { it.isNotBlank() }?.toUiImage()
    }

    return GalleryDetail(
        orientation = GalleryOrientation.Vertical,
        statusKey = statusKey,
        accountType = AccountType.Specific(accountKey),
        url = "https://linzijun.app/video/detail?id=$id",
        images = imageUrls.toImmutableList(),
        title = title ?: "",
        author = author,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        content = buildString {
            append(title ?: "")
            if (contentShort != null) append("\n$contentShort")
            if (durationHr != null) append("\n时长: $durationHr")
            if (priceDesc != null) append("\n价格: $priceDesc")
        }.toUiPlainText(),
        isBookmarked = false,
        bookmarkAction = ClickEvent.Noop,
        matrix = persistentListOf(),
    )
}

// ==================== 视频详情 (video_detail) ====================

internal fun LzjVideoDetailItem.toGalleryDetail(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
): GalleryDetail {
    val author = if (uid != null) {
        UiProfile(
            key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
            handle = UiHandle(raw = "用户 #$uid", host = CBART_HOST),
            avatar = null,
            nameInternal = "用户 #$uid".toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        )
    } else null

    val imageUrls = images.mapNotNull { img ->
        val url = if (img.path?.startsWith("http") == true) img.path else "$LZJ_CDN${img.path}"
        url.takeIf { it.isNotBlank() }?.toUiImage()
    }

    val priceDesc = priceDesc ?: if ((price ?: 0) > 0) "$price 金币" else if ((priceDiamond ?: 0) > 0) "$priceDiamond 钻石" else "免费"

    return GalleryDetail(
        orientation = GalleryOrientation.Vertical,
        statusKey = statusKey,
        accountType = AccountType.Specific(accountKey),
        url = "https://linzijun.app/video/detail?id=$id",
        images = imageUrls.toImmutableList(),
        title = title ?: "",
        author = author,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        content = buildString {
            append(title ?: "")
            if (content != null) append("\n\n$content")
            if (durationHr != null) append("\n时 长: $durationHr")
            if (extraText2 != null) append("\n大 小: $extraText2")
            append("\n价 格: $priceDesc")
            if (purchasedNum != null) append("\n已购买: $purchasedNum 次")
            if (favNum != null) append("\n收 藏: $favNum 次")
            if (hasPreview == 1) append("\n可预览")
            if (hasRepo == 1) append("\n可下载")
            if (canWatchOnline == 1) append("\n可在线观看")
        }.toUiPlainText(),
        isBookmarked = false,
        bookmarkAction = ClickEvent.Noop,
        matrix = persistentListOf(),
    )
}

// ==================== 精选内容 (featured_video) ====================

internal fun LzjFeaturedVideoItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val isVideo = contentType == "video"
    val avatarUrl = avatar?.let { if (it.startsWith("http")) it else "$LZJ_CDN$it" }

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = true,
        contentWarning = UiTranslatableText(
            (if (isVideo) "🎬 视频" else "🖼️ 图集").toUiPlainText(),
        ),
        user = UiProfile(
            key = MicroBlogKey(id = id ?: "", host = CBART_HOST),
            handle = UiHandle(raw = postBy ?: "妖狐", host = CBART_HOST),
            avatar = avatarUrl?.toUiImage(),
            nameInternal = (postBy ?: "妖狐").toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(title?.toUiPlainText() ?: "".toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = "fv_${id}", host = CBART_HOST),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_featured_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_featured_$id")
}

// ==================== 作者/工作室 ====================

/**
 * 妖狐吧作者 -> UiTimelineV2
 */
internal fun LzjProducerItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val avatarFullUrl = avatar?.let { if (it.startsWith("http")) it else "$LZJ_CDN$it" }
    val displayName = nickName ?: username ?: "作者 #$uid"
    val fansCount = followerNum?.toLongOrNull() ?: 0

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = UiTranslatableText("🏪 作者".toUiPlainText()),
        user = UiProfile(
            key = MicroBlogKey(id = uid, host = CBART_HOST),
            handle = UiHandle(raw = displayName, host = CBART_HOST),
            avatar = avatarFullUrl?.toUiImage(),
            nameInternal = displayName.toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = nickName?.toUiPlainText(),
            matrices = UiProfile.Matrices(
                fansCount = fansCount,
                followsCount = 0, statusesCount = 0,
            ),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText(buildString {
            totalPost?.let { append("📦 $it 内容") }
        }.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = uid, host = CBART_HOST),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_producer_$uid",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_producer_$uid")
}

// ==================== 评论 ====================

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
            key = MicroBlogKey(id = uid ?: "", host = CBART_HOST),
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
        statusKey = MicroBlogKey(id = "comment_$id", host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_comment_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_comment_$id")
}

// ==================== 消息 ====================

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
            key = MicroBlogKey(id = uid ?: "", host = CBART_HOST),
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
        statusKey = MicroBlogKey(id = "msg_$id", host = CBART_HOST),
        card = null,
        createdAt = (postTime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_msg_$id",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_msg_$id")
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

internal fun LzjVideoDetailItem.toUiTimelineItem(accountKey: MicroBlogKey): UiTimelineV2 {
    val imageUrls = images.take(4).mapNotNull { img ->
        val url = if (img.path?.startsWith("http") == true) img.path else "$LZJ_CDN${img.path}"
        url.takeIf { it.isNotBlank() }?.toUiImage()
    }
    val priceDesc = priceDesc ?: if ((price ?: 0) > 0) "$price 金币" else if ((priceDiamond ?: 0) > 0) "$priceDiamond 钻石" else "免费"
    val videoId = id?.toString() ?: ""

    val post = UiTimelineV2.Post(
        platformType = PlatformType.Cbart,
        images = imageUrls.toImmutableList(),
        sensitive = true,
        contentWarning = UiTranslatableText(
            buildString {
                append("🎬 $priceDesc")
                if (durationHr != null) append(" · $durationHr")
                if (hasPreview == 1) append(" · 可预览")
            }.toUiPlainText(),
        ),
        user = UiProfile(
            key = MicroBlogKey(id = uid?.toString() ?: "", host = CBART_HOST),
            handle = UiHandle(raw = "用户 #$uid", host = CBART_HOST),
            avatar = null,
            nameInternal = "用户 #$uid".toUiPlainText(),
            platformType = PlatformType.Cbart,
            clickEvent = ClickEvent.Noop,
            banner = null, description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(), bottomContent = null,
        ),
        content = UiTranslatableText((title ?: "").toUiPlainText()),
        actions = persistentListOf(
            ActionMenu.cbartFavourite(
                statusKey = MicroBlogKey(id = "vl_$videoId", host = CBART_HOST),
                favourited = false,
                count = (favNum?.toLong() ?: 0).coerceAtLeast(0),
                accountKey = accountKey,
            ),
            ActionMenu.Item(
                updateKey = "cbart_more_${id}",
                icon = UiIcon.More,
                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                actionFamily = PostActionFamily.Share,
                clickEvent = ClickEvent.Noop,
            ),
        ),
        poll = null,
        statusKey = MicroBlogKey(id = "vl_$videoId", host = CBART_HOST),
        card = null,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        emojiReactions = persistentListOf(), sourceChannel = null, visibility = null,
        replyToHandle = null, references = persistentListOf(),
        clickEvent = ClickEvent.Deeplink(
            DeeplinkRoute.Status.Detail(
                statusKey = MicroBlogKey(id = "vl_$videoId", host = CBART_HOST),
                accountType = AccountType.Specific(accountKey),
            ),
        ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
        accountType = AccountType.Specific(accountKey),
        itemKey = "lzj_purchased_$videoId",
    )
    return UiTimelineV2.TimelinePostItem(post = post, itemKey = "lzj_purchased_$videoId")
}
