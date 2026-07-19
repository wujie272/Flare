package dev.dimension.flare.data.datasource.zhihu

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
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.zhihu.ZhihuService
import dev.dimension.flare.data.platform.ZHIHU_HOST
import dev.dimension.flare.data.platform.ZhihuCredential
import dev.dimension.flare.data.platform.ZhihuPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal class ZhihuDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<ZhihuCredential>,
    private val updateCredential: suspend (ZhihuCredential) -> Unit,
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
    
    val service = ZhihuService(
        credentialFlow = credentialFlow,
        onCredentialRefreshed = updateCredential,
    )
    private val loader by lazy { ZhihuLoader(service = service) }

    override val userHandler by lazy { UserHandler(host = accountKey.host, loader = loader) }
    override val postHandler by lazy { PostHandler(accountType = AccountType.Specific(accountKey), loader = loader) }
    override val postEventHandler by lazy { PostEventHandler(accountType = AccountType.Specific(accountKey), handler = this) }
    override val relationHandler by lazy { RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader) }
    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType> = 
        loader.supportedTypes

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader as NotificationLoader,
        )
    }
    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)
    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> = emptyList()

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            ZhihuPlatformSpec.recommendTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Zhihu),
                title = UiText.Raw("知乎"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            ZhihuPlatformSpec.recommendTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Zhihu),
            ),
            ZhihuPlatformSpec.hotTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Featured),
            ),
            ZhihuPlatformSpec.dailyTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Search),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Home,
                icon = UiIcon.Home,
                target = ShortcutSpec.Target.Timeline(
                    ZhihuPlatformSpec.recommendTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Featured,
                icon = UiIcon.Featured,
                target = ShortcutSpec.Target.Timeline(
                    ZhihuPlatformSpec.hotTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Discover,
                icon = UiIcon.Search,
                target = ShortcutSpec.Target.Timeline(
                    ZhihuPlatformSpec.dailyTimelineSpec.candidate(data = TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = 
        ZhihuRecommendPagingLoader(service = service, accountKey = accountKey)
    
    override fun userTimeline(userKey: MicroBlogKey, mediaOnly: Boolean): RemoteLoader<UiTimelineV2> {
        if (mediaOnly) return notSupported()
        return ZhihuUserTimelineLoader(service = service, accountKey = accountKey, userKey = userKey, type = "answers")
    }
    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = 
        ZhihuCommentsLoader(service = service, accountKey = accountKey, statusKey = statusKey)
    
    override fun galleryDetail(statusKey: MicroBlogKey): dev.dimension.flare.common.Cacheable<GalleryDetail> = 
        dev.dimension.flare.common.Cacheable(
            fetchSource = {},
            cacheSource = {
                kotlinx.coroutines.flow.flowOf(
                    GalleryDetail(
                        orientation = GalleryOrientation.Vertical,
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                        url = "https://www.zhihu.com/question/0/answer/${statusKey.id}",
                        images = persistentListOf(),
                        title = "",
                        author = null,
                        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                        content = null,
                        isBookmarked = false,
                        bookmarkAction = dev.dimension.flare.ui.model.ClickEvent.Noop,
                        matrix = persistentListOf(),
                    )
                )
            },
        )
    
    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = 
        ZhihuCommentsLoader(service = service, accountKey = accountKey, statusKey = statusKey)
    override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = 
        ZhihuHotTimelineLoader(service = service, accountKey = accountKey)
    
    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = 
        ZhihuSearchTimelineLoader(service = service, accountKey = accountKey, query = query)
    
    override fun searchUser(query: String): RemoteLoader<UiProfile> = 
        ZhihuSearchUserLoader(service = service, accountKey = accountKey, query = query)
    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()
    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = 
        ZhihuDailyTimelineLoader(service = service, accountKey = accountKey)
    
    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()
    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = 
        ZhihuFolloweesLoader(service = service, accountKey = accountKey, userKey = userKey)
    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = 
        ZhihuFollowersLoader(service = service, accountKey = accountKey, userKey = userKey)
    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = 
        persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                loader = ZhihuUserTimelineLoader(service = service, accountKey = accountKey, userKey = userKey, type = "answers"),
            ),
            ProfileTab(
                name = UiStrings.Articles,
                loader = ZhihuUserTimelineLoader(service = service, accountKey = accountKey, userKey = userKey, type = "articles"),
            ),
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = 
        ZhihuNotificationTimelineLoader(service = service, accountKey = accountKey)

    override suspend fun handle(event: PostEvent, updater: DatabaseUpdater) {
        require(event is PostEvent.Zhihu)
        when (event) {
            is PostEvent.Zhihu.VoteUp -> handleVoteUp(event)
            is PostEvent.Zhihu.Bookmark -> handleBookmark(event)
            is PostEvent.Zhihu.Follow -> handleFollow(event)
        }
    }

    private suspend fun handleVoteUp(event: PostEvent.Zhihu.VoteUp) {
        // 从 statusKey 判断是 answer 还是 article
        // 知乎的 id 格式: answer_{id} 或 article_{id}
        val id = event.postKey.id
        val voteType = if (event.voted) "clear" else "up"
        when {
            id.startsWith("article_") -> service.voteArticle(id.removePrefix("article_"), voteType)
            else -> service.voteAnswer(id, voteType)
        }
    }

    private suspend fun handleBookmark(event: PostEvent.Zhihu.Bookmark) {
        val id = event.postKey.id
        val contentType: String
        val contentId: String
        when {
            id.startsWith("article_") -> {
                contentType = "article"; contentId = id.removePrefix("article_")
            }
            else -> {
                contentType = "answer"; contentId = id
            }
        }
        service.bookmarkContent(contentType, contentId, !event.bookmarked)
    }

    private suspend fun handleFollow(event: PostEvent.Zhihu.Follow) {
        if (event.following) {
            service.unfollowMember(event.postKey.id)
        } else {
            service.followMember(event.postKey.id)
        }
    }

    fun hotTimelineLoader(): RemoteLoader<UiTimelineV2> = ZhihuHotTimelineLoader(service = service, accountKey = accountKey)
    fun dailyTimelineLoader(): RemoteLoader<UiTimelineV2> = ZhihuDailyTimelineLoader(service = service, accountKey = accountKey)
    fun recommendTimelineLoader(): RemoteLoader<UiTimelineV2> = ZhihuRecommendPagingLoader(service = service, accountKey = accountKey)
}
