package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.toutiao.ToutiaoDataSource
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
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.ToutiaoLoginProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public const val TOUTIAO_HOST: String = "www.toutiao.com"

@HiddenFromObjC
public data object ToutiaoPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by ToutiaoLoginProvider {
    override val type: PlatformType = PlatformType.Toutiao
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "今日头条",
            icon = UiIcon.Toutiao,
        )

    internal val hotTimelineSpec =
        TimelineSpec(
            id = "toutiao.hot",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<ToutiaoDataSource, TimelineSpec.AccountBasedData> {
                    hotTimelineLoader()
                },
        )

    internal val recommendTimelineSpec =
        TimelineSpec(
            id = "toutiao.recommend",
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<ToutiaoDataSource, TimelineSpec.AccountBasedData> {
                    recommendTimelineLoader()
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            hotTimelineSpec,
            recommendTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://www.toutiao.com/article/{id}",
                serializer = ToutiaoArticleDeepLink.serializer(),
                callback = { data ->
                    dev.dimension.flare.ui.route.DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, TOUTIAO_HOST),
                    )
                },
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        ToutiaoDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(ToutiaoCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = ToutiaoCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(host: String, locale: String): MicroblogDataSource =
        throw UnsupportedOperationException("Toutiao guest data source is not supported")
}

@Serializable
private data class ToutiaoArticleDeepLink(
    val id: String,
)
