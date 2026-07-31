package dev.dimension.flare.data.platform.bilibili

import dev.dimension.flare.data.datasource.bilibili.BilibiliDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.BilibiliLoginProvider
import kotlinx.collections.immutable.ImmutableList
import dev.dimension.flare.data.datasource.bilibili.BilibiliHomeRemoteLoader
import dev.dimension.flare.data.datasource.bilibili.BilibiliPopularRemoteLoader
import dev.dimension.flare.data.datasource.bilibili.BilibiliDynamicRemoteLoader
import dev.dimension.flare.data.datasource.bilibili.BilibiliRankingRemoteLoader
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object BilibiliPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by BilibiliLoginProvider {
    override val type: PlatformType = PlatformType.Bilibili
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Bilibili",
            icon = UiIcon.Featured,
        )

    internal val homeTimelineSpec = TimelineSpec(
        id = "bilibili.home",
        title = UiStrings.Home,
        icon = IconType.Material(UiIcon.Home),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<BilibiliDataSource, TimelineSpec.AccountBasedData> {
            BilibiliHomeRemoteLoader(service = service, accountKey = accountKey)
        },
    )

    internal val popularTimelineSpec = TimelineSpec(
        id = "bilibili.popular",
        title = UiStrings.Featured,
        icon = IconType.Material(UiIcon.Featured),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<BilibiliDataSource, TimelineSpec.AccountBasedData> {
            BilibiliPopularRemoteLoader(service = service, accountKey = accountKey)

        },
    )

    internal val dynamicTimelineSpec = TimelineSpec(
        id = "bilibili.dynamic",
        title = UiStrings.Following,
        icon = IconType.Material(UiIcon.Rss),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<BilibiliDataSource, TimelineSpec.AccountBasedData> {
            BilibiliDynamicRemoteLoader(service = service, accountKey = accountKey)
        },
    )

    internal val rankingTimelineSpec = TimelineSpec(
        id = "bilibili.ranking",
        title = UiStrings.List,
        icon = IconType.Material(UiIcon.List),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = accountLoader<BilibiliDataSource, TimelineSpec.AccountBasedData> {
            BilibiliRankingRemoteLoader(service = service, accountKey = accountKey)
        },
    )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf(
        homeTimelineSpec,
        dynamicTimelineSpec,
        rankingTimelineSpec,
        popularTimelineSpec,
    )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        BilibiliDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(BilibiliCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(BilibiliCredential.serializer(), credential)
            },
        )

    override fun guestDataSource(host: String, locale: String): MicroblogDataSource =
        BilibiliDataSource.guest()
}
