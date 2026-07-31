package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.data.network.cbart.api.*
import dev.dimension.flare.data.platform.CbartCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

/**
 * 妖狐吧 API 服务层
 * 仅使用 linzijun.app（Laravel 新站）
 */
internal class CbartService(
    credentialFlow: Flow<CbartCredential>,
    private val accountKey: MicroBlogKey? = null,
    onCredentialUpdated: suspend (CbartCredential) -> Unit = {},
) {
    val api = CbartApiClient(
        credentialFlow = credentialFlow,
        accountKey = accountKey,
        onCredentialUpdated = onCredentialUpdated,
    )

    suspend fun fetchArticles(page: Int = 1, limit: Int = 20): List<LzjArticleItem> =
        api.articleList(page = page, limit = limit)?.list ?: emptyList()

    suspend fun fetchVideoList(
        page: Int = 1, limit: Int = 20, order: String? = "posttime",
        area: String? = null, rank: String? = null, filterOneday: Boolean = false,
        fav: Boolean = false, purchased: Boolean = false, keyword: String? = null,
    ): List<LzjVideoListItem> = api.videoList(page = page, limit = limit, order = order,
        area = area, rank = rank, filterOneday = filterOneday, fav = fav, purchased = purchased, keyword = keyword
    )?.data?.contents ?: emptyList()

    suspend fun fetchVideoDetail(videoId: String): LzjVideoDetailItem? =
        api.videoDetail(videoId = videoId)?.data?.contents?.firstOrNull()

    /** 收藏/取消收藏 */
    suspend fun toggleVideoFav(videoId: String): Boolean = api.toggleVideoFav(videoId = videoId)


    /** 获取当前用户信息 */
    suspend fun fetchUserInfo() = api.fetchUserInfo()
}
