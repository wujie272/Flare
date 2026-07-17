package dev.dimension.flare.data.datasource.cbart

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
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
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.time.Instant
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.flow.flowOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

internal class CbartDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<CbartCredential>,
    private val updateCredential: suspend (CbartCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
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
    override val supportedRelationTypes: Set<RelationActionType> = emptySet()
    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> = emptyList()

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CbartPlatformSpec.newTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Cbart),
                title = UiText.Raw("Cbart"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CbartPlatformSpec.newTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey), icon = IconType.Material(UiIcon.Cbart)),
            CbartPlatformSpec.hotTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
            CbartPlatformSpec.favouriteTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(title = UiStrings.Discover, icon = UiIcon.Search, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.newTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.Featured, icon = UiIcon.Featured, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.hotTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
            ShortcutSpec(title = UiStrings.Favourite, icon = UiIcon.Heart, target = ShortcutSpec.Target.Timeline(CbartPlatformSpec.favouriteTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)))),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = CbartHomeTimelineLoader(service = service, accountKey = accountKey)
    override fun userTimeline(userKey: MicroBlogKey, mediaOnly: Boolean): RemoteLoader<UiTimelineV2> = notSupported()
    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()
    override fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail> = Cacheable(
        fetchSource = {},
        cacheSource = {
            flowOf(GalleryDetail(
                orientation = GalleryOrientation.Vertical, statusKey = statusKey, accountType = AccountType.Specific(accountKey),
                url = "https://cbart.net/picture/detail?id=${statusKey.id}", images = persistentListOf(),
                title = "", author = null, createdAt = Instant.fromEpochMilliseconds(0).toUi(), content = null,
                isBookmarked = false, bookmarkAction = ClickEvent.Noop, matrix = persistentListOf(),
            ))
        },
    )
    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = CbartGalleryCommentsLoader(service = service, accountKey = accountKey)
    override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = CbartHomeTimelineLoader(service = service, accountKey = accountKey)
    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = CbartHomeTimelineLoader(service = service, accountKey = accountKey)
    override fun searchUser(query: String): RemoteLoader<UiProfile> = CbartSearchUserLoader(service = service, accountKey = accountKey)
    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()
    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = CbartDiscoverTimelineLoader(service = service, accountKey = accountKey)
    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()
    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()
    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()
    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
    override suspend fun handle(event: PostEvent, updater: DatabaseUpdater) {}

    fun newTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartHomeTimelineLoader(service = service, accountKey = accountKey)
    fun hotTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartHotTimelineLoader(service = service, accountKey = accountKey)
    fun favouriteTimelineLoader(): RemoteLoader<UiTimelineV2> = CbartFavouriteTimelineLoader(service = service, accountKey = accountKey)
}
