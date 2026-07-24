package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.NotificationFilter
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
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.CbartLoginProvider
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
            displayName = "妖狐吧",
            icon = UiIcon.Cbart,
        )

    internal val announcementTimelineSpec =
        TimelineSpec(
            id = "lzj.announcement",
            title = UiStrings.Announcement,
            icon = UiIcon.Info.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    articleTimelineLoader()
                },
        )

    internal val videoTimelineSpec =
        TimelineSpec(
            id = "lzj.video",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    videoTimelineLoader()
                },
        )

    internal val pictureTimelineSpec =
        TimelineSpec(
            id = "lzj.pictures",
            title = UiStrings.Media,
            icon = UiIcon.Eye.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    pictureTimelineLoader()
                },
        )

    internal val producerTimelineSpec =
        TimelineSpec(
            id = "lzj.producers",
            title = UiStrings.Discover,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    producerTimelineLoader()
                },
        )

    internal val fuliTimelineSpec =
        TimelineSpec(
            id = "lzj.fuli",
            title = UiStrings.Default,
            icon = UiIcon.Favourite.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    fuliTimelineLoader()
                },
        )

    internal val notificationTimelineSpec =
        TimelineSpec(
            id = "lzj.notification",
            title = UiStrings.Notifications,
            icon = UiIcon.Notification.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<CbartDataSource, TimelineSpec.AccountBasedData> {
                    notification(NotificationFilter.All)
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            announcementTimelineSpec,
            videoTimelineSpec,
            pictureTimelineSpec,
            producerTimelineSpec,
            fuliTimelineSpec,
            notificationTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf()

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





