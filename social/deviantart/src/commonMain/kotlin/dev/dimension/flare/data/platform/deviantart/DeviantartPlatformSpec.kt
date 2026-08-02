package dev.dimension.flare.data.platform.deviantart

import dev.dimension.flare.data.datasource.deviantart.DeviantartDataSource
import dev.dimension.flare.data.datasource.deviantart.DeviantartHomeLoader
import dev.dimension.flare.data.datasource.deviantart.DeviantartHotLoader
import dev.dimension.flare.data.datasource.deviantart.DeviantartNewestLoader
import dev.dimension.flare.data.datasource.deviantart.DeviantartPopularLoader
import dev.dimension.flare.data.datasource.deviantart.DeviantartFavouritesLoader
import dev.dimension.flare.data.datasource.deviantart.DeviantartTopicsLoader
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.DeviantartLoginProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object DeviantartPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by DeviantartLoginProvider {
    override val type: PlatformType = PlatformType.Deviantart
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "DeviantArt",
            icon = UiIcon.Art,
        )

    internal val homeTimelineSpec = TimelineSpec(
        id = "deviantart.home",
        title = UiStrings.Home,
        icon = IconType.Material(UiIcon.Home),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartHomeLoader(service = service, accountKey = accountKey)
        },
    )

    internal val hotTimelineSpec = TimelineSpec(
        id = "deviantart.hot",
        title = UiStrings.Featured,
        icon = IconType.Material(UiIcon.Featured),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartHotLoader(service = service, accountKey = accountKey)
        },
    )

    internal val popularTimelineSpec = TimelineSpec(
        id = "deviantart.popular",
        title = UiStrings.Home,
        icon = IconType.Material(UiIcon.Home),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartPopularLoader(service = service, accountKey = accountKey)
        },
    )

    internal val newestTimelineSpec = TimelineSpec(
        id = "deviantart.newest",
        title = UiStrings.Discover,
        icon = IconType.Material(UiIcon.Search),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartNewestLoader(service = service, accountKey = accountKey)
        },
    )

    internal val topicsTimelineSpec = TimelineSpec(
        id = "deviantart.topics",
        title = UiStrings.Featured,
        icon = IconType.Material(UiIcon.Art),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartTopicsLoader(service = service, accountKey = accountKey)
        },
    )

    internal val favouriteTimelineSpec = TimelineSpec(
        id = "deviantart.favourite",
        title = UiStrings.Favourite,
        icon = IconType.Material(UiIcon.Heart),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<DeviantartDataSource, TimelineSpec.AccountBasedData> {
            DeviantartFavouritesLoader(
                service = service,
                accountKey = accountKey,
                username = accountKey.id,
            )
        },
    )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            homeTimelineSpec,
            hotTimelineSpec,
            popularTimelineSpec,
            newestTimelineSpec,
            topicsTimelineSpec,
            favouriteTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://www.deviantart.com/{artist}/art/{deviationId}",
                serializer = DeviantartDeviationDeepLink.serializer(),
                callback = { data ->
                    dev.dimension.flare.ui.route.DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.deviationId, "deviantart.com"),
                    )
                },
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        DeviantartDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(DeviantartCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = DeviantartCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(host: String, locale: String): MicroblogDataSource =
        throw UnsupportedOperationException("DeviantArt guest data source is not supported")
}

@Serializable
private data class DeviantartDeviationDeepLink(
    val artist: String,
    val deviationId: String,
)
