package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.coolapk.CoolapkDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.CoolapkLoginProvider
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object CoolapkPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by CoolapkLoginProvider {
    override val platformId: String = "Coolapk"
    override val metadata: PlatformMetadata =
        PlatformMetadata(
            displayName = "酷安",
            icon = UiIcon.Coolapk,
        )

    private val homeTimelineSpec =
        TimelineSpec(
            id = "coolapk.home",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CoolapkDataSource, TimelineSpec.AccountBasedData> { dataSource ->
                    homeTimelineLoader()
                },
        )

    private val coolPicTimelineSpec =
        TimelineSpec(
            id = "coolapk.coolpic",
            title = UiStrings.Discover,
            icon = UiIcon.Art.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CoolapkDataSource, TimelineSpec.AccountBasedData> { dataSource ->
                    coolPicTimelineLoader()
                },
        )

    private val followTimelineSpec =
        TimelineSpec(
            id = "coolapk.follow",
            title = UiStrings.Following,
            icon = UiIcon.Follow.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CoolapkDataSource, TimelineSpec.AccountBasedData> { dataSource ->
                    followTimelineLoader()
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            homeTimelineSpec,
            followTimelineSpec,
            coolPicTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = CoolapkDataSource(context)

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("Coolapk guest data source is not supported yet")
}
