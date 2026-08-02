package dev.dimension.flare.data.datasource.bilibili

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.model.mapper.bilibiliLike
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
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
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.bilibili.BilibiliService
import dev.dimension.flare.data.platform.bilibili.BilibiliCredential
import dev.dimension.flare.data.platform.bilibili.BilibiliPlatformSpec
import kotlinx.serialization.json.jsonPrimitive
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Bilibili 数据源
 */
internal class BilibiliDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<BilibiliCredential>,
    private val updateCredential: suspend (BilibiliCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    ComposeDataSource,
    NotificationTimelineDataSource,
    NotificationDataSource,
    TimelineTabConfigurationDataSource,
    UserDataSource,
    PostDataSource,
    RelationDataSource,
    PostEventHandler.Handler {

    val service = BilibiliService(
        credentialFlow = credentialFlow,
    )
    private val loader by lazy { BilibiliLoader(accountKey = accountKey, service = service) }

    override val userHandler by lazy { UserHandler(host = accountKey.host, loader = loader) }
    override val postHandler by lazy { PostHandler(accountType = AccountType.Specific(accountKey), loader = loader) }
    override val postEventHandler by lazy { PostEventHandler(accountType = AccountType.Specific(accountKey), handler = this) }
    override val relationHandler by lazy { RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader) }
    override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }
    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)

    // ==================== Tab 配置 ====================

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            BilibiliPlatformSpec.homeTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Home),
                title = UiText.Raw("推荐"),
            ),
            BilibiliPlatformSpec.dynamicTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Rss),
                title = UiText.Raw("关注"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            BilibiliPlatformSpec.homeTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Home),
                title = UiText.Raw("推荐"),
            ),
            BilibiliPlatformSpec.dynamicTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Rss),
                title = UiText.Raw("关注"),
            ),
            BilibiliPlatformSpec.rankingTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.List),
                title = UiText.Raw("最新"),
            ),
            BilibiliPlatformSpec.popularTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Featured),
                title = UiText.Raw("热门"),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf()
    }

    // ==================== Timeline ====================

    override fun homeTimeline() = BilibiliHomeRemoteLoader(service = service, accountKey = accountKey)

    override fun userTimeline(userKey: MicroBlogKey, mediaOnly: Boolean): RemoteLoader<UiTimelineV2> = notSupported()

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        BilibiliVideoDetailMediator(
            service = service,
            accountKey = accountKey,
            bvid = statusKey.id,
        )
    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = BilibiliSearchLoader(service = service, accountKey = accountKey, query = query)

    override fun searchUser(query: String): RemoteLoader<UiProfile> = BilibiliSearchUserLoader(service = service, accountKey = accountKey, query = query)

    // ==================== Discover ====================

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()
    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()
    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    // ==================== Relation ====================

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()
    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    // ==================== Profile ====================

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> {
        val mid = userKey.id.toLongOrNull() ?: return persistentListOf()
        return persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                loader = BilibiliUserVideosLoader(service = service, accountKey = accountKey, mid = mid),
            ),
        )
    }

    // ==================== Notification ====================

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = notSupported()

    // ==================== Compose ====================

    override suspend fun compose(data: ComposeData, progress: () -> Unit) = Unit

    override fun composeConfig(type: ComposeType): ComposeConfig = ComposeConfig(
        text = ComposeConfig.Text(2000),
        media = ComposeConfig.Media(0, false, altTextMaxLength = -1, allowMediaOnly = false),
    )

    // ==================== Event ====================

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Bilibili)
        when (event) {
            is PostEvent.Bilibili.Like -> {
                // Bilibili API 需要 aid，从 postKey.id (bvid) 获取
                val videoInfo = service.getVideoInfo(event.postKey.id)
                val aid = videoInfo?.get("aid")?.jsonPrimitive?.content?.toLongOrNull()
                if (aid != null) {
                    val result = service.likeVideo(aid = aid, like = !event.liked)
                    if (result) {
                        updater.updateActionMenu(
                            postKey = event.postKey,
                            newActionMenu =
                                ActionMenu.bilibiliLike(
                                    statusKey = event.postKey,
                                    liked = !event.liked,
                                    count = event.count + if (!event.liked) 1 else -1,
                                    accountKey = event.accountKey,
                                ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun guest(): Nothing = throw UnsupportedOperationException("Guest mode not supported")
    }
}
