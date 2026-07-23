package dev.dimension.flare.data.platform

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.wordpress.toUiTimelineItem
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.wordpress.WordPressApi
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SubscriptionTimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public const val WORDPRESS_HOST: String = "wordpress.org"

@HiddenFromObjC
public data object WordPressPlatformSpec : PlatformSpec {
    override val type: PlatformType = PlatformType.WordPress
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "WordPress",
            icon = UiIcon.WordPress,
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf()

    override val subscriptionTimelineSpecs: ImmutableList<SubscriptionTimelineSpec> =
        persistentListOf(
            WordPressSubscriptionTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        throw UnsupportedOperationException("WordPress no longer supports account-based data source. Use subscription instead.")

    override fun guestDataSource(host: String, locale: String): MicroblogDataSource =
        throw UnsupportedOperationException("WordPress no longer supports guest data source. Use subscription instead.")
}

/**
 * WordPress 订阅时间线 Spec
 * 允许用户通过订阅系统添加任意 WordPress 站点
 */
@HiddenFromObjC
internal data object WordPressSubscriptionTimelineSpec : SubscriptionTimelineSpec {
    override val type: SubscriptionType = SubscriptionType.WORDPRESS

    override suspend fun isAvailable(
        host: String,
        locale: String,
    ): Boolean {
        return runCatching {
            val api = WordPressApi(baseUrl = host)
            val siteInfo = api.detectSite()
            siteInfo?.namespaces?.contains("wp/v2") == true
        }.getOrDefault(false)
    }

    override fun createLoader(
        host: String,
        locale: String,
    ): CacheableRemoteLoader<UiTimelineV2> {
        val api = WordPressApi(baseUrl = host)
        val url = "https://${host.removePrefix("http://").removePrefix("https://")}"
        return WordPressSubscriptionLoader(api = api, siteUrl = url)
    }
}

/**
 * WordPress 订阅 Loader
 * 从单个 WordPress 站点加载文章，用于订阅模式
 */
internal class WordPressSubscriptionLoader(
    private val api: WordPressApi,
    private val siteUrl: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    private val accountKey = MicroBlogKey(id = siteUrl.removePrefix("https://"), host = siteUrl.removePrefix("https://"))

    override val pagingKey: String = "wp_sub_${accountKey.id}"
    override val supportPrepend: Boolean = false

    override suspend fun load(pageSize: Int, request: PagingRequest): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) return PagingResult(endOfPaginationReached = true)
        val page = when (request) {
            is PagingRequest.Refresh -> 1
            is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
        }
        val posts = api.fetchPosts(page = page, perPage = pageSize)
        val siteName = siteUrl.removePrefix("https://")
        return PagingResult(
            data = posts.map { it.toUiTimelineItem(accountKey, siteName) },
            nextKey = if (posts.isEmpty()) null else (page + 1).toString(),
        )
    }
}
