package dev.dimension.flare.data.datasource.deviantart

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
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
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.deviantart.DeviantartService

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.dimension.flare.data.platform.deviantart.DeviantartCredential
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal class DeviantartDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<DeviantartCredential>,
    private val updateCredential: suspend (DeviantartCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    ComposeDataSource,
    NotificationTimelineDataSource,
    NotificationDataSource,
    TimelineTabConfigurationDataSource,
    GalleryDataSource,
    UserDataSource,
    PostDataSource,
    RelationDataSource,
    PostEventHandler.Handler {

    val service = DeviantartService(
        credentialFlow = credentialFlow,
        onCredentialRefreshed = updateCredential,
    )
    private val loader by lazy { DeviantartLoader(accountKey = accountKey, service = service) }

    override val userHandler by lazy { UserHandler(host = accountKey.host, loader = loader) }
    override val postHandler by lazy { PostHandler(accountType = AccountType.Specific(accountKey), loader = loader) }
    override val postEventHandler by lazy { PostEventHandler(accountType = AccountType.Specific(accountKey), handler = this) }
    override val relationHandler by lazy { RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader) }
    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType> =
        loader.supportedTypes

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader as dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader,
        )
    }
    override val supportedNotificationFilter: List<NotificationFilter> = listOf(NotificationFilter.All)

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            newestTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Search),
                title = UiText.Raw("Discover"),
            ),
            topicsTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Art),
                title = UiText.Raw("Topics"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            homeTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Home),
                title = UiText.Raw("Home"),
            ),
            hotTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Featured),
                title = UiText.Raw("Hot"),
            ),
            popularTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Home),
                title = UiText.Raw("Popular"),
            ),
            newestTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Search),
                title = UiText.Raw("Discover"),
            ),
            favouriteTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Home,
                icon = UiIcon.Home,
                target = ShortcutSpec.Target.Timeline(
                    homeTimelineSpec.galleryCandidate(data = TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Featured,
                icon = UiIcon.Featured,
                target = ShortcutSpec.Target.Timeline(
                    hotTimelineSpec.galleryCandidate(data = TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Favourite,
                icon = UiIcon.Heart,
                target = ShortcutSpec.Target.Timeline(
                    favouriteTimelineSpec.galleryCandidate(data = TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        DeviantartHomeLoader(service = service, accountKey = accountKey)

    override fun userTimeline(userKey: MicroBlogKey, mediaOnly: Boolean): RemoteLoader<UiTimelineV2> {
        if (mediaOnly) return notSupported()
        return DeviantartUserGalleryLoader(service = service, accountKey = accountKey, username = userKey.id)
    }

    override fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail> {
        val deviationId = statusKey.id
        return Cacheable(
            fetchSource = {},
            cacheSource = {
                kotlinx.coroutines.flow.flow {
                    val detail = service.deviationDetail(deviationId)
                    if (detail != null) {
                        val images = listOfNotNull(
                            detail.contentUrl?.let {
                                dev.dimension.flare.ui.model.UiMedia.Image(
                                    url = it,
                                    previewUrl = detail.previewUrl ?: detail.thumbnailUrl ?: it,
                                    width = 0f, height = 0f,
                                    description = detail.title, sensitive = false,
                                )
                            } ?: detail.previewUrl?.let {
                                dev.dimension.flare.ui.model.UiMedia.Image(
                                    url = it,
                                    previewUrl = detail.thumbnailUrl ?: it,
                                    width = 0f, height = 0f,
                                    description = detail.title, sensitive = false,
                                )
                            },
                        )
                        emit(
                            GalleryDetail(
                                orientation = GalleryOrientation.Vertical,
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                                url = "https://www.deviantart.com/${detail.artistName}/art/$deviationId",
                                images = images.toImmutableList(),
                                title = detail.title,
                                author = detail.artistName.let { name ->
                                    UiProfile(
                                        key = MicroBlogKey(id = name, host = "deviantart.com"),
                                        handle = dev.dimension.flare.ui.model.UiHandle(
                                            raw = "$name@deviantart.com", host = "deviantart.com",
                                        ),
                                        avatar = detail.artistAvatar?.let {
                                            dev.dimension.flare.ui.model.UiMedia.Image(
                                                url = it, previewUrl = it, description = name,
                                                height = 0f, width = 0f, sensitive = false,
                                            )
                                        },
                                        nameInternal = name.toUiPlainText(),
                                        platformType = dev.dimension.flare.model.PlatformType.Deviantart,
                                        clickEvent = dev.dimension.flare.ui.model.ClickEvent.Deeplink(
                                            dev.dimension.flare.ui.route.DeeplinkRoute.Profile.User(
                                                accountType = AccountType.Specific(accountKey),
                                                userKey = MicroBlogKey(id = name, host = "deviantart.com"),
                                            )
                                        ),
                                        banner = null, description = null,
                                        matrices = UiProfile.Matrices(0, 0, 0),
                                        mark = persistentListOf(), bottomContent = null,
                                    )
                                },
                                createdAt = if (detail.published > 0) Instant.fromEpochMilliseconds(detail.published * 1000).toUi()
                                    else Instant.fromEpochMilliseconds(0).toUi(),
                                content = detail.description?.toUiPlainText(),
                                isBookmarked = detail.isFavourite,
                                bookmarkAction = dev.dimension.flare.ui.model.ClickEvent.event(
                                    accountKey,
                                    PostEvent.Deviantart.Favourite(
                                        postKey = statusKey,
                                        favourited = detail.isFavourite,
                                        accountKey = accountKey,
                                    ),
                                ),
                                matrix = persistentListOf(),
                            )
                        )
                    } else {
                        emit(
                            GalleryDetail(
                                orientation = GalleryOrientation.Vertical,
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                                url = "https://www.deviantart.com/art/$deviationId",
                                images = persistentListOf(), title = "", author = null,
                                createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                                content = null, isBookmarked = false,
                                bookmarkAction = dev.dimension.flare.ui.model.ClickEvent.Noop,
                                matrix = persistentListOf(),
                            )
                        )
                    }
                }
            },
        )
    }

    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        DeviantartCommentsLoader(service = service, accountKey = accountKey, deviationId = statusKey.id)

    override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        DeviantartMoreLikeThisLoader(service = service, accountKey = accountKey, deviationId = statusKey.id)

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> {
        val deviationId = statusKey.id
        return object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey: String = "da_context_${deviationId}_$accountKey"
            override val supportPrepend: Boolean = false

            override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
                // 加载详情 + 评论
                val detail = service.deviationDetail(deviationId)
                val detailItem = detail?.toUiDetailItem(accountKey)
                val comments = service.fetchComments(deviationId, limit = pageSize)
                val items = mutableListOf<UiTimelineV2>()
                detailItem?.let { items.add(it) }
                items.addAll(comments.data.map { it.toUiTimelineItem(accountKey) })
                return PagingResult(
                    data = items,
                    endOfPaginationReached = comments.isEnd,
                    nextKey = comments.nextOffset?.toString(),
                )
            }
        }
    }

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        DeviantartSearchLoader(service = service, accountKey = accountKey, query = query)

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        DeviantartUserSearchLoader(service = service, accountKey = accountKey, query = query)

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        DeviantartUserSearchLoader(service = service, accountKey = accountKey, query = "popular")

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> =
        DeviantartHomeLoader(service = service, accountKey = accountKey)

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        DeviantartTagSearchLoader(service = service, accountKey = accountKey, tag = "digitalart")

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        DeviantartFollowingLoader(service = service, accountKey = accountKey, username = userKey.id)

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        DeviantartFansLoader(service = service, accountKey = accountKey, username = userKey.id)

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                displayType = ProfileTab.DisplayType.Gallery,
                showAllImagesInGallery = true,
                loader = DeviantartUserGalleryLoader(service = service, accountKey = accountKey, username = userKey.id),
            ),
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        DeviantartNotificationLoader(service = service, accountKey = accountKey)

    override suspend fun handle(event: PostEvent, updater: DatabaseUpdater) {
        require(event is PostEvent.Deviantart)
        when (event) {
            is PostEvent.Deviantart.Favourite -> {
                if (event.favourited) {
                    service.unfaveDeviation(event.postKey.id)
                } else {
                    service.faveDeviation(event.postKey.id)
                }
            }
        }
    }

    override suspend fun compose(data: ComposeData, progress: () -> Unit) {
        val referenceStatus = data.referenceStatus
        val composeStatus = referenceStatus?.composeStatus
        if (composeStatus is dev.dimension.flare.ui.presenter.compose.ComposeStatus.Reply) {
            service.postComment(composeStatus.statusKey.id, data.content)
        }
    }

    override fun composeConfig(type: ComposeType): ComposeConfig = ComposeConfig(
        text = ComposeConfig.Text(2000),
        media = ComposeConfig.Media(0, false, altTextMaxLength = -1, allowMediaOnly = false),
    )

    // ========== Timeline Specs ==========

    internal val homeTimelineSpec = TimelineSpec(
        id = "deviantart.home",
        title = UiStrings.Home,
        icon = IconType.Material(UiIcon.Home),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = dev.dimension.flare.data.model.tab.accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartHomeLoader(service = service, accountKey = accountKey)
        },
    )

    internal val hotTimelineSpec = TimelineSpec(
        id = "deviantart.hot",
        title = UiStrings.Featured,
        icon = IconType.Material(UiIcon.Featured),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = dev.dimension.flare.data.model.tab.accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartHotLoader(service = service, accountKey = accountKey)
        },
    )

    internal val popularTimelineSpec = TimelineSpec(
        id = "deviantart.popular",
        title = UiStrings.Home,
        icon = IconType.Material(UiIcon.Home),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = dev.dimension.flare.data.model.tab.accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartPopularLoader(service = service, accountKey = accountKey)
        },
    )

    internal val newestTimelineSpec = TimelineSpec(
        id = "deviantart.newest",
        title = UiStrings.Discover,
        icon = IconType.Material(UiIcon.Search),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = dev.dimension.flare.data.model.tab.accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartNewestLoader(service = service, accountKey = accountKey)
        },
    )

    internal val topicsTimelineSpec = TimelineSpec(
        id = "deviantart.topics",
        title = UiStrings.Featured,
        icon = IconType.Material(UiIcon.Art),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = dev.dimension.flare.data.model.tab.accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartTopicsLoader(service = service, accountKey = accountKey)
        },
    )

    internal val favouriteTimelineSpec = TimelineSpec(
        id = "deviantart.favourite",
        title = UiStrings.Favourite,
        icon = IconType.Material(UiIcon.Heart),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = dev.dimension.flare.data.model.tab.accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartFavouritesLoader(
                service = service,
                accountKey = accountKey,
                username = accountKey.id,
            )
        },
    )
}

// ========== Loaders ==========

internal class DeviantartHomeLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_home_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        // 1. Try _puppy API home feed (deviations from users you watch)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val puppyResult = service.fetchHomeFeed(page = page)
        if (puppyResult.data.isNotEmpty()) {
            return PagingResult(
                data = puppyResult.data.map { it.toUiTimelineItem(accountKey) },
                endOfPaginationReached = puppyResult.isEnd,
                nextKey = puppyResult.nextOffset?.toString(),
            )
        }
        // 2. Fallback: daily deviations
        val fallback = service.browseDailyDeviations()
        return PagingResult(
            data = fallback.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = true,
            nextKey = null,
        )
    }
}

internal class DeviantartHotLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_hot_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        // /browse/hot was removed by DeviantArt in 2024-07
        // Fallback: browse by tag "digitalart"
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.browseByTag(tag = "digitalart", offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartPopularLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_popular_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        // /browse/popular was removed by DeviantArt in 2024-07
        // Fallback: browse by tag "popular"
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.browseByTag(tag = "popular", offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartNewestLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_newest_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        // /browse/newest was removed by DeviantArt in 2024-07
        // 优先使用 _puppy API 的推荐流（Recommended For You）
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val rfyResult = service.fetchRfyFeed(page = page)
        if (rfyResult.data.isNotEmpty()) {
            return PagingResult(
                data = rfyResult.data.map { it.toUiTimelineItem(accountKey) },
                endOfPaginationReached = rfyResult.isEnd,
                nextKey = rfyResult.nextOffset?.toString(),
            )
        }
        // _puppy API 不可用时（如未登录），回退到每日精选
        val fallback = service.browseDailyDeviations()
        return PagingResult(
            data = fallback.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = true,
            nextKey = null,
        )
    }
}

internal class DeviantartUserGalleryLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val username: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_gallery_${username}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.userGallery(username, offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartFollowingLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val username: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "deviantart_following_${username}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.userFriends(username, offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiProfile(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartFansLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val username: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "deviantart_fans_${username}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.userWatchers(username, offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiProfile(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

// ========== New Loaders ==========

internal class DeviantartCommentsLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val deviationId: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "da_comments_${deviationId}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.fetchComments(deviationId, offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartMoreLikeThisLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val deviationId: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "da_related_${deviationId}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.moreLikeThis(deviationId, offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartNotificationLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "da_notifications_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.fetchNotifications(offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartSearchLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "da_search_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.fetchSearchAll(query = query, page = page)
        if (result.data.isNotEmpty()) {
            return PagingResult(
                data = result.data.map { it.toUiTimelineItem(accountKey) },
                endOfPaginationReached = result.isEnd,
                nextKey = result.nextOffset?.toString(),
            )
        }
        return PagingResult(data = emptyList(), endOfPaginationReached = true)
    }
}

internal class DeviantartUserSearchLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "da_user_search_${query}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val profile = service.fetchPuppyUserProfile(username = query)
        if (profile != null) {
            val owner = profile["owner"]?.jsonObject ?: profile["gruser"]?.jsonObject?.get("owner")?.jsonObject
            val username = owner?.get("username")?.jsonPrimitive?.content
            if (username != null) {
                val userProfile = service.userProfile(username)
                if (userProfile != null) {
                    return PagingResult(
                        data = listOf(userProfile.toUiProfile(accountKey)),
                        endOfPaginationReached = true,
                    )
                }
            }
        }
        return PagingResult(data = emptyList(), endOfPaginationReached = true)
    }
}

internal class DeviantartTagSearchLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val tag: String,
) : CacheableRemoteLoader<UiHashtag> {
    override val pagingKey: String = "da_tag_${tag}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiHashtag> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.fetchTagSearch(tag = tag, page = page)
        return PagingResult(
            data = result.data.map { item ->
                UiHashtag(
                    hashtag = tag,
                    description = item.title,
                    searchContent = tag,
                )
            },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartFavouritesLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
    private val username: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_favourites_${username}_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.userFavourites(username, offset = offset, limit = pageSize)
        return PagingResult(
            data = result.data.map { it.toUiTimelineItem(accountKey) },
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}

internal class DeviantartTopicsLoader(
    private val service: DeviantartService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "deviantart_topics_$accountKey"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull() ?: 0
        val result = service.browseTopics(offset = offset, limit = pageSize.coerceAtMost(10))
        // Flatten example deviations from all topics into a single timeline
        val items = result.data.flatMap { topic ->
            topic.exampleDeviations.map { it.toUiTimelineItem(accountKey) }
        }
        return PagingResult(
            data = items,
            endOfPaginationReached = result.isEnd,
            nextKey = result.nextOffset?.toString(),
        )
    }
}
