package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
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
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlin.time.Instant
import kotlinx.coroutines.flow.firstOrNull
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
        accountKey = accountKey,
        onCredentialUpdated = updateCredential,
    )
    private val loader by lazy { CbartLoader(service = service, credentialFlow = credentialFlow) }

    init {
        // 启动时检查 session，只在没有 session 时初始化
        // 已有的 session 让 419 自动重试机制处理
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            val cred = credentialFlow.firstOrNull()
            if (cred?.laravelSession == null) {
                // 首次使用或 session 丢失，需要初始化
                // 三步握手：GET / → POST /api/video_list → GET /（绑定 CSRF）
                service.api.refreshLaravelSession()?.let { updateCredential(it) }
            }
            // 已有 session → 直接用，30分钟TTL + 419自动重试兜底
        }
    }

    override val userHandler by lazy { UserHandler(host = accountKey.host, loader = loader) }
    override val postHandler by lazy { PostHandler(accountType = AccountType.Specific(accountKey), loader = loader) }
    override val postEventHandler by lazy { PostEventHandler(accountType = AccountType.Specific(accountKey), handler = this) }
    override val relationHandler by lazy { RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader) }
    override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes

    override val notificationHandler by lazy {
        NotificationHandler(accountKey = accountKey, loader = loader as NotificationLoader)
    }
    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)
    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> = emptyList()
    override val defaultTabs: ImmutableList<TimelineCandidate<*>> = persistentListOf()

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CbartPlatformSpec.announcementTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
            CbartPlatformSpec.videoTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart), title = UiText.Raw("视频")),
            CbartPlatformSpec.pictureTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart), title = UiText.Raw("图集")),
            CbartPlatformSpec.producerTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart), title = UiText.Raw("作者")),
            CbartPlatformSpec.fuliTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart), title = UiText.Raw("福利")),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(title = UiStrings.Discover, icon = UiIcon.List, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.videoTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.LatestResource, icon = UiIcon.Eye, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.pictureTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.Announcement, icon = UiIcon.Info, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.announcementTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = CbartArticleTimelineLoader(service = service, accountKey = accountKey)

    override fun userTimeline(userKey: MicroBlogKey, mediaOnly: Boolean): RemoteLoader<UiTimelineV2> {
        if (mediaOnly) return notSupported()
        return CbartUserContentLoader(service = service, accountKey = accountKey, userKey = userKey)
    }

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()

    override fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail> {
        val videoId = statusKey.id.removePrefix("vl_")
        return Cacheable<GalleryDetail>(
            fetchSource = {},
            cacheSource = {
                flow {
                    // 直接调 video_detail API（linzijun.app，有凭证可通）
                    val videoDetail = service.fetchVideoDetail(videoId = videoId)
                    if (videoDetail != null) {
                        emit(videoDetail.toGalleryDetail(statusKey, accountKey))
                        return@flow
                    }
                    // 查不到就显示空
                    emit(emptyGalleryDetail(statusKey))
                }
            },
        )
    }

    private fun emptyGalleryDetail(statusKey: MicroBlogKey): GalleryDetail = GalleryDetail(
        orientation = GalleryOrientation.Vertical, statusKey = statusKey, accountType = AccountType.Specific(accountKey),
        url = "https://linzijun.app/video/list?id=${statusKey.id}", images = persistentListOf(),
        title = "", author = null, createdAt = Instant.fromEpochMilliseconds(0).toUi(), content = null,
        isBookmarked = false, bookmarkAction = ClickEvent.Noop, matrix = persistentListOf(),
    )

    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> {
        return CbartCommentsLoader(service = service, accountKey = accountKey, videoId = statusKey.id)
    }

    override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        CbartVideoTimelineLoader(service = service, accountKey = accountKey)

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()
    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()
    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()
    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = CbartProducerTimelineLoader(service = service, accountKey = accountKey)
    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()
    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = CbartFollowingLoader(service = service, accountKey = accountKey)
    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> {
        return persistentListOf(
            ProfileTab(name = UiStrings.Posts, loader = CbartUserContentLoader(service = service, accountKey = accountKey, userKey = userKey)),
            ProfileTab(name = UiStrings.Favourite, loader = CbartFavVideoLoader(service = service, accountKey = accountKey)),
            ProfileTab(name = UiStrings.PurchasedVideo, loader = CbartPurchasedVideoLoader(service = service, accountKey = accountKey, credentialFlow = credentialFlow)),
        ).toImmutableList()
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
        service.toggleVideoFav(event.postKey.id)
    }

    private suspend fun handleFollow(event: PostEvent.Cbart.Follow) {
        // 妖狐吧暂不支持关注操作
    }

    fun fuliTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartFuliLoader(service = service, accountKey = accountKey)

    fun articleTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartArticleTimelineLoader(service = service, accountKey = accountKey)
    fun videoTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartVideoTimelineLoader(service = service, accountKey = accountKey)
    fun pictureTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartPictureTimelineLoader(service = service, accountKey = accountKey)
    fun producerTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartProducerTimelineLoader(service = service, accountKey = accountKey)
}
