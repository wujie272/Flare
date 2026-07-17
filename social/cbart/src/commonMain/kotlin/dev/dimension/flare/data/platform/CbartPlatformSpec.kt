package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.cbart.CbartDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
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
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.CbartLoginProvider
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public const val CBART_HOST: String = "cbart.net"

@HiddenFromObjC
public data object CbartPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by CbartLoginProvider {
    override val type: PlatformType = PlatformType.Cbart
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Cbart",
            icon = UiIcon.Cbart,
        )

    internal val newTimelineSpec =
        TimelineSpec(
            id = "cbart.new",
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    newTimelineLoader()
                },
        )

    internal val hotTimelineSpec =
        TimelineSpec(
            id = "cbart.hot",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    hotTimelineLoader()
                },
        )

    internal val favouriteTimelineSpec =
        TimelineSpec(
            id = "cbart.favourite",
            title = UiStrings.Favourite,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    favouriteTimelineLoader()
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            newTimelineSpec,
            hotTimelineSpec,
            favouriteTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://cbart.net/picture/detail?id={id}",
                serializer = CbartPicDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, CBART_HOST),
                    )
                },
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        CbartDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(CbartCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = CbartCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(host: String, locale: String): MicroblogDataSource =
        throw UnsupportedOperationException("Cbart guest data source is not supported")
}

@Serializable
private data class CbartPicDeepLink(
    val id: String,
)
