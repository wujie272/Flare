package dev.dimension.flare.data.datasource.coolapk
import kotlinx.serialization.json.JsonObject

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.mapper.coolapkLike
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.coolapk.CoolapkService
import dev.dimension.flare.data.platform.CoolapkCredential
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public class CoolapkDataSource(
    private val context: PlatformDataSourceContext,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    RelationDataSource,
    NotificationTimelineDataSource,
    NotificationDataSource,
    TimelineTabConfigurationDataSource,
    PostDataSource,
    PostEventHandler.Handler {
    private val credentialFlow = context.credentialFlow(CoolapkCredential.serializer())

    internal val service: CoolapkService =
        CoolapkService(
            credentialFlow = credentialFlow,
        )

    private val loader by lazy { CoolapkLoader(accountKey = accountKey, service = service) }

    private val notificationBadgeStore: CoolapkNotificationBadgeStore by lazy {
        CoolapkNotificationBadgeStore(loader) { total ->
            notificationHandler.update(total)
        }
    }

    override val accountKey: MicroBlogKey = context.accountKey

    override val userHandler: UserHandler by lazy { UserHandler(host = accountKey.host, loader = loader) }
    override val postHandler: PostHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }
    override val postEventHandler: PostEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }
    override val relationHandler: RelationHandler =
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes

    override val notificationHandler: NotificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
            fetchBadgeCount = {
                notificationBadgeStore.refreshAndGetTotal()
            },
        )
    }

    override val supportedNotificationFilter: List<NotificationFilter> =
        listOf(
            NotificationFilter.Mention,
            NotificationFilter.Comment,
            NotificationFilter.Like,
        )

    // ==================== PostEventHandler.Handler ====================

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Coolapk)
        when (event) {
            is PostEvent.Coolapk.Like -> {
                val result = if (event.liked) {
                    service.unlike(event.postKey.id)
                } else {
                    service.like(event.postKey.id)
                }
                if (result) {
                    // Update action menu in cache
                    updater.updateActionMenu(
                        postKey = event.postKey,
                        newActionMenu =
                            ActionMenu.coolapkLike(
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

    // ==================== TimelineTabConfigurationDataSource ====================

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home
                .candidate(
                    data = TimelineSpec.AccountBasedData(accountKey),
                    icon = IconType.Material(UiIcon.Coolapk),
                    title = UiText.Raw("酷安"),
                ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Coolapk),
            ),
        )
    }

    override val shortcuts: ImmutableList<dev.dimension.flare.data.model.tab.ShortcutSpec> by lazy {
        persistentListOf()
    }

    // ==================== 公开 Loader 工厂 ====================

    public fun homeTimelineLoader(): RemoteLoader<UiTimelineV2> =
        CoolapkHomeTimelineLoader(accountKey = accountKey, service = service)

    public fun coolPicTimelineLoader(): RemoteLoader<UiTimelineV2> =
        CoolapkCoolPicTimelineLoader(accountKey = accountKey, service = service)

    public fun followTimelineLoader(): RemoteLoader<UiTimelineV2> =
        CoolapkFollowTimelineLoader(accountKey = accountKey, service = service)

    // ==================== MicroblogDataSource ====================

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        CoolapkHomeTimelineLoader(accountKey = accountKey, service = service)

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All -> {
                CoolapkAllNotificationMediator(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = { notificationBadgeStore.clearAll() },
                )
            }
            NotificationFilter.Mention -> {
                CoolapkNotificationMediator(
                    service = service,
                    accountKey = accountKey,
                    type = "atme",
                    onClearMarker = { notificationBadgeStore.clear(NotificationFilter.Mention) },
                )
            }
            NotificationFilter.Comment -> {
                CoolapkNotificationMediator(
                    service = service,
                    accountKey = accountKey,
                    type = "comment",
                    onClearMarker = { notificationBadgeStore.clear(NotificationFilter.Comment) },
                )
            }
            NotificationFilter.Like -> {
                CoolapkNotificationMediator(
                    service = service,
                    accountKey = accountKey,
                    type = "like",
                    onClearMarker = { notificationBadgeStore.clear(NotificationFilter.Like) },
                )
            }
        }

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        CoolapkUserTimelineMediator(
            service = service,
            accountKey = accountKey,
            userKey = userKey,
            mediaOnly = mediaOnly,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        CoolapkContextMediator(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        CoolapkSearchStatusMediator(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        CoolapkSearchUserPagingSource(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> =
        // 复用首页接口作为发现页
        CoolapkHomeTimelineLoader(accountKey = accountKey, service = service)

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        CoolapkFollowingPagingSource(
            service = service,
            accountKey = accountKey,
            userKey = userKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        CoolapkFansPagingSource(
            service = service,
            accountKey = accountKey,
            userKey = userKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
}
