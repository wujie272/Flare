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
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlin.time.Instant
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

    val service = CbartService(credentialFlow = credentialFlow)
    private val loader by lazy { CbartLoader(service = service) }

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

    override fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail> = Cacheable(
        fetchSource = {},
        cacheSource = {
            flow {
                val videoId = statusKey.id.toLongOrNull()
                if (videoId == null) {
                    emit(emptyGalleryDetail(statusKey))
                } else {
                    val video = service.fetchVideoById(videoId)
                    if (video == null) {
                        emit(emptyGalleryDetail(statusKey))
                    } else {
                        emit(video.toGalleryDetail(statusKey, accountKey))
                    }
                }
            }
        },
    )

    private fun emptyGalleryDetail(statusKey: MicroBlogKey): GalleryDetail = GalleryDetail(
        orientation = GalleryOrientation.Vertical, statusKey = statusKey, accountType = AccountType.Specific(accountKey),
        url = "https://shenmatk.com/video/detail?id=${statusKey.id}", images = persistentListOf(),
        title = "", author = null, createdAt = Instant.fromEpochMilliseconds(0).toUi(), content = null,
        isBookmarked = false, bookmarkAction = ClickEvent.Noop, matrix = persistentListOf(),
    )

    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> {
        val videoId = statusKey.id.toLongOrNull() ?: return notSupported()
        return CbartCommentsLoader(service = service, accountKey = accountKey, videoId = videoId)
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
        val videoId = event.postKey.id.toLongOrNull() ?: return
        service.toggleVideoFav(videoId)
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
