package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import dev.dimension.flare.data.platform.CbartPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.data.network.cbart.api.CbartVideoDetailItem
import dev.dimension.flare.data.network.cbart.api.CbartVideoOwner
import kotlin.time.Instant
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.flow.flow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

internal class CbartDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<CbartCredential>,
    private val updateCredential: suspend (CbartCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    NotificationTimelineDataSource,
    NotificationDataSource,
    PinnableTimelineTabDataSource,
    TimelineTabConfigurationDataSource,
    GalleryDataSource,
    UserDataSource,
    PostDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    val service = CbartService(
        credentialFlow = credentialFlow,
        onCredentialRefreshed = updateCredential,
    )
    private val loader by lazy { CbartLoader(service = service) }

    override val userHandler by lazy { UserHandler(host = accountKey.host, loader = loader) }
    override val postHandler by lazy { PostHandler(accountType = AccountType.Specific(accountKey), loader = loader) }
    override val postEventHandler by lazy { PostEventHandler(accountType = AccountType.Specific(accountKey), handler = this) }
    override val relationHandler by lazy { RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader) }
    override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader as NotificationLoader,
        )
    }
    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)
    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> = emptyList()

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf()
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CbartPlatformSpec.announcementTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
            CbartPlatformSpec.latestResourceTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart)),
            CbartPlatformSpec.discoverTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart), title = UiText.Raw("工作室")),
            CbartPlatformSpec.hotTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(title = UiStrings.Discover, icon = UiIcon.List, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.discoverTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.Featured, icon = UiIcon.Featured, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.hotTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.LatestResource, icon = UiIcon.Eye, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.latestResourceTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.Announcement, icon = UiIcon.Info, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.announcementTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = CbartArticleTimelineLoader(service = service, accountKey = accountKey)
    override fun userTimeline(userKey: MicroBlogKey, mediaOnly: Boolean): RemoteLoader<UiTimelineV2> {
        if (mediaOnly) return notSupported()
        return CbartUserContentLoader(service = service, accountKey = accountKey, userKey = userKey)
    }
    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()
    override fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail> = Cacheable(
        fetchSource = {},
        cacheSource = {
            flow {
                val videoId = statusKey.id.toLongOrNull()
                if (videoId == null) {
                    emit(emptyGalleryDetail(statusKey))
                } else {
                    val detail = service.fetchVideoDetail(videoId)
                    if (detail == null) {
                        emit(emptyGalleryDetail(statusKey))
                    } else {
                        emit(detail.toGalleryDetail(statusKey, accountKey))
                    }
                }
            }
        },
    )
    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = CbartGalleryCommentsLoader(service = service, accountKey = accountKey, statusKey = statusKey)
    override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = CbartHotTimelineLoader(service = service, accountKey = accountKey)
    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = 
        CbartStudioSearchLoader(service = service, accountKey = accountKey, query = query)
    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()
    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()
    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = CbartDiscoverTimelineLoader(service = service, accountKey = accountKey)
    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()
    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = CbartFollowingLoader(service = service)
    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()
    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> {
        val base = persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                loader = CbartUserContentLoader(service = service, accountKey = accountKey, userKey = userKey),
            ),
        )
        return if (userKey == accountKey) {
            (base + ProfileTab(
                name = UiStrings.PurchasedVideo,
                loader = CbartPurchasedVideoLoader(service = service, accountKey = accountKey),
            )).toImmutableList()
        } else {
            base
        }
    }

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = 
        CbartNotificationTimelineLoader(service = service, accountKey = accountKey)

    override suspend fun handle(event: PostEvent, updater: DatabaseUpdater) {
        require(event is PostEvent.Cbart)
        when (event) {
            is PostEvent.Cbart.Favourite -> handleFavourite(event)
            is PostEvent.Cbart.Follow -> handleFollow(event)
        }
    }

    private suspend fun handleFavourite(event: PostEvent.Cbart.Favourite) {
        val videoId = event.postKey.id.toLongOrNull() ?: return
        service.toggleVideoFav(videoId)
    }

    private suspend fun handleFollow(event: PostEvent.Cbart.Follow) {
        val fromUid = runCatching { service.fetchNumericUid() }.getOrNull()
            ?: accountKey.id.toLongOrNull()
            ?: return
        val toUid = event.postKey.id.toLongOrNull() ?: return
        service.toggleFollow(fromUid, toUid, follow = event.following)
    }

    fun articleTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartArticleTimelineLoader(service = service, accountKey = accountKey)
    fun latestResourceTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartLatestResourceTimelineLoader(service = service, accountKey = accountKey)
    fun discoverTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartDiscoverTimelineLoader(service = service, accountKey = accountKey)
    fun hotTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartHotTimelineLoader(service = service, accountKey = accountKey)

    private fun emptyGalleryDetail(statusKey: MicroBlogKey): GalleryDetail = GalleryDetail(
        orientation = GalleryOrientation.Vertical, statusKey = statusKey, accountType = AccountType.Specific(accountKey),
        url = "https://www.linzijiang.app/video/detail?id=${statusKey.id}", images = persistentListOf(),
        title = "", author = null, createdAt = Instant.fromEpochMilliseconds(0).toUi(), content = null,
        isBookmarked = false, bookmarkAction = ClickEvent.Noop, matrix = persistentListOf(),
    )
}

/**
 * 将视频详情数据映射为 GalleryDetail
 */
internal fun CbartVideoDetailItem.toGalleryDetail(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
): GalleryDetail {
    val images = images?.mapNotNull { img ->
        val url = img.path
        if (url.isNullOrBlank()) null
        else UiMedia.Image(
            url = url,
            previewUrl = img.mPath ?: img.mobPath ?: url,
            description = title,
            height = (img.imageHeight ?: 0).toFloat(),
            width = (img.imageWidth ?: 0).toFloat(),
            sensitive = true,
        )
    }.orEmpty().toImmutableList()

    val author = owner?.toUiProfile()

    val bookmarkAction = ClickEvent.event(
        accountKey = accountKey,
        postEvent = dev.dimension.flare.data.datasource.microblog.PostEvent.Cbart.Favourite(
            postKey = statusKey,
            favourited = isFav == 1,
            count = (favNum ?: 0).toLong(),
            accountKey = accountKey,
        ),
    )

    val durationText = if (durationS != null && durationS > 0) {
        val hours = durationS / 3600
        val minutes = (durationS % 3600) / 60
        val seconds = durationS % 60
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    } else ""

    val sizeText = extraText2 ?: ""

    val matrix = buildList {
        if (playerShowedNum != null && playerShowedNum > 0) {
            add(GalleryDetail.Matrix(
                icon = UiIcon.Eye,
                count = playerShowedNum.toLong(),
            ))
        }
        if (favNum != null && favNum > 0) {
            add(GalleryDetail.Matrix(
                icon = UiIcon.Favourite,
                count = favNum.toLong(),
            ))
        }
        if (durationText.isNotEmpty()) {
            add(GalleryDetail.Matrix(
                icon = UiIcon.Clock,
                count = 0,
            ))
        }
    }.toImmutableList()

    val contentText = buildString {
        if (content != null && content.isNotBlank()) {
            append(content)
        }
        if (supplymentContent != null && supplymentContent.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(supplymentContent)
        }
        if (sizeText.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("📦 $sizeText")
        }
        if (canWatchOnline == 1) {
            if (isNotEmpty()) append("\n")
            append("▶️ 可在线观看")
        }
        if (priceDiamond != null && priceDiamond > 0) {
            if (isNotEmpty()) append("\n")
            append("💎 $priceDiamond")
        }
        if (purchasedNum != null && purchasedNum.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("🛒 $purchasedNum 已购买")
        }
    }.takeIf { it.isNotEmpty() }

    return GalleryDetail(
        orientation = GalleryOrientation.Vertical,
        statusKey = statusKey,
        accountType = AccountType.Specific(accountKey),
        url = "https://www.linzijiang.app/video/detail?id=${statusKey.id}",
        images = images,
        title = title ?: "",
        author = author,
        createdAt = (posttime?.let { tryParseDate(it) } ?: Instant.fromEpochMilliseconds(0)).toUi(),
        content = contentText?.toUiPlainText(),
        isBookmarked = isFav == 1,
        bookmarkAction = bookmarkAction,
        matrix = matrix,
    )
}

/**
 * 将视频 owner 映射为 UiProfile
 */
internal fun CbartVideoOwner.toUiProfile(): UiProfile {
    val name = nickName ?: displayName ?: username ?: uid.toString()
    // avatarUrl 已经是完整 URL，avatar 是相对路径需拼 CDN
    val avatarFullUrl = avatarUrl ?: avatar?.let { "https://www.tpzf001.com$it" }
    return UiProfile(
        key = MicroBlogKey(id = uid.toString(), host = CBART_HOST),
        handle = dev.dimension.flare.ui.model.UiHandle(raw = name, host = CBART_HOST),
        avatar = avatarFullUrl?.toUiImage(),
        nameInternal = name.toUiPlainText(),
        platformType = dev.dimension.flare.model.PlatformType.Cbart,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        matrices = dev.dimension.flare.ui.model.UiProfile.Matrices(
            fansCount = (followerNum ?: 0).toLong(),
            followsCount = 0,
            statusesCount = 0,
        ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

/**
 * 尝试解析 "2026-07-09 23:19:16" 格式的时间戳
 */
internal fun tryParseDate(dateStr: String): Instant? {
    return try {
        val iso = dateStr.replace(" ", "T") + "Z"
        Instant.parse(iso)
    } catch (_: Exception) {
        null
    }
}

