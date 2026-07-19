package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.zhihu.ZhihuDataSource
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
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.ZhihuLoginProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public const val ZHIHU_HOST: String = "www.zhihu.com"

@HiddenFromObjC
public data object ZhihuPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by ZhihuLoginProvider {
    override val type: PlatformType = PlatformType.Zhihu
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "知乎",
            icon = UiIcon.Zhihu,
        )

    internal val hotTimelineSpec =
        TimelineSpec(
            id = "zhihu.hot",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<ZhihuDataSource, TimelineSpec.AccountBasedData> {
                    hotTimelineLoader()
                },
        )

    internal val dailyTimelineSpec =
        TimelineSpec(
            id = "zhihu.daily",
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<ZhihuDataSource, TimelineSpec.AccountBasedData> {
                    dailyTimelineLoader()
                },
        )

    internal val recommendTimelineSpec =
        TimelineSpec(
            id = "zhihu.recommend",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<ZhihuDataSource, TimelineSpec.AccountBasedData> {
                    recommendTimelineLoader()
                },
        )

    internal val notificationTimelineSpec =
        TimelineSpec(
            id = "zhihu.notification",
            title = UiStrings.Notifications,
            icon = UiIcon.Notification.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<ZhihuDataSource, TimelineSpec.AccountBasedData> {
                    notification(NotificationFilter.All)
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            recommendTimelineSpec,
            hotTimelineSpec,
            dailyTimelineSpec,
            notificationTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://www.zhihu.com/question/{questionId}/answer/{answerId}",
                serializer = ZhihuAnswerDeepLink.serializer(),
                callback = { data ->
                    dev.dimension.flare.ui.route.DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.answerId, ZHIHU_HOST),
                    )
                },
            ),
            PlatformDeepLink(
                uriPattern = "https://zhuanlan.zhihu.com/p/{articleId}",
                serializer = ZhihuArticleDeepLink.serializer(),
                callback = { data ->
                    dev.dimension.flare.ui.route.DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.articleId, ZHIHU_HOST),
                    )
                },
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        ZhihuDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(ZhihuCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = ZhihuCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(host: String, locale: String): MicroblogDataSource =
        throw UnsupportedOperationException("Zhihu guest data source is not supported")
}

@Serializable
private data class ZhihuAnswerDeepLink(
    val questionId: String,
    val answerId: String,
)

@Serializable
private data class ZhihuArticleDeepLink(
    val articleId: String,
)
