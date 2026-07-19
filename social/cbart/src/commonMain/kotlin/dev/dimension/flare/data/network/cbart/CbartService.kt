package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.data.network.cbart.api.*
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

internal class CbartService(
    credentialFlow: Flow<CbartCredential>,
    onCredentialRefreshed: suspend (CbartCredential) -> Unit = {},
) {
    val api = CbartApiClient(credentialFlow = credentialFlow)
    private val credentialFlowRef = credentialFlow

    suspend fun fetchArticles(page: Int = 1, limit: Int = 20): List<CbartArticleItem> {
        val response = api.articleList(page = page, limit = limit, news = 1)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchLatestResources(page: Int = 1, limit: Int = 50): List<CbartNewContentItem> {
        val response = api.newContentList(page = page, limit = limit)
        return response?.data?.newContent ?: emptyList()
    }

    suspend fun fetchMyContent(page: Int = 1): List<CbartContentItem> {
        val uid = currentUid() ?: return emptyList()
        val response = api.contentList(uid = uid, page = page)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchUserContent(uid: String, page: Int = 1): List<CbartContentItem> {
        val response = api.contentList(uid = uid, page = page)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchMyBlogs(page: Int = 1): List<CbartBlogItem> {
        val uid = currentUid() ?: return emptyList()
        val response = api.blogList(uid = uid, page = page)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchVideos(page: Int = 1, limit: Int = 20, uid: String? = null): List<CbartVideoItem> {
        val response = api.videoList(page = page, limit = limit, uid = uid)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchPurchasedVideos(page: Int = 1): List<CbartVideoItem> {
        val response = api.videoList(page = page, purchasedVideo = true)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchStudios(keyword: String = "", page: Int = 1, order: String = "updatetime"): List<CbartStudioItem> {
        val response = api.studioList(keyword = keyword, page = page, order = order)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchTiers(id: String, page: Int = 1): List<CbartTierItem> {
        val response = api.tierList(id = id, page = page)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchMessages(page: Int = 1): List<CbartMessageItem> {
        val response = api.messageUserList(page = page)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 搜索工作室 ====================

    suspend fun searchStudios(keyword: String, page: Int = 1): List<CbartStudioItem> {
        val response = api.studioList(keyword = keyword, page = page, limit = 20)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 收藏/取消收藏 ====================

    suspend fun favouriteContent(contentId: Long, contentType: String = "video"): Boolean {
        val response = api.favouriteContent(contentId = contentId, contentType = contentType)
        return response?.code == 200
    }

    suspend fun unfavouriteContent(contentId: Long, contentType: String = "video"): Boolean {
        val response = api.unfavouriteContent(contentId = contentId, contentType = contentType)
        return response?.code == 200
    }

    // ==================== 关注/取消关注工作室 ====================

    suspend fun followStudio(studioId: Long): Boolean {
        val response = api.followStudio(studioId = studioId)
        return response?.code == 200
    }

    suspend fun unfollowStudio(studioId: Long): Boolean {
        val response = api.unfollowStudio(studioId = studioId)
        return response?.code == 200
    }

    suspend fun isStudioFollowed(studioId: Long): Boolean {
        val response = api.studioList(keyword = "", page = 1, limit = 1, needOwner = 1, needStudio = 1, contentNum = 0, filterQuery = studioId.toString())
        return response?.data?.contents?.firstOrNull()?.isFollowed == true
    }

    // ==================== 用户信息 ====================

    suspend fun fetchUsernameFromHomePage(): Pair<String, String>? {
        val html = api.fetchHomePage() ?: return null
        val regex = Regex("""navi_user_panel[^>]*>\s*([^<]+?)\s*<span""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html)
        if (match != null) {
            val username = match.groupValues[1].trim()
            if (username.isNotBlank()) {
                return Pair(username, username)
            }
        }
        return null
    }

    suspend fun fetchProfileInfo(): Pair<String?, String?>? {
        val html = api.fetchProfilePage() ?: return null
        val avatarRegex = Regex("""avatar_url"\s*:\s*"([^"]+)"""")
        val avatarUrl = avatarRegex.find(html)?.groupValues?.get(1)?.trim()?.replace("\\/", "/")
        val nickRegex = Regex("""nick_name"\s*:\s*"([^"]+)"""")
        val nickName = nickRegex.find(html)?.groupValues?.get(1)?.trim()
        if (avatarUrl != null || nickName != null) {
            return Pair(avatarUrl, nickName)
        }
        return null
    }

    suspend fun fetchFollowedStudioCount(): Int {
        val uid = fetchNumericUid() ?: return 0
        val response = api.studioList(
            keyword = "", page = 1, limit = 1,
            followedByUid = uid,
        )
        return response?.data?.totalNum ?: 0
    }

    suspend fun fetchFollowedStudios(page: Int = 1): Pair<List<CbartStudioItem>, Int> {
        val uid = fetchNumericUid() ?: return Pair(emptyList(), 0)
        val response = api.studioList(
            keyword = "", page = page, limit = 20,
            followedByUid = uid,
        )
        val items = response?.data?.contents ?: emptyList()
        val total = response?.data?.totalNum ?: items.size
        return Pair(items, total)
    }

    private suspend fun currentUid(): String? {
        return currentCredential()?.userId?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchNumericUid(): Long? {
        val html = api.fetchHomePage() ?: return null
        val meUid = Regex("""meJSON\.uid\s*=\s*(\d+)""").find(html)
        if (meUid != null) {
            val uid = meUid.groupValues[1].toLongOrNull()
            if (uid != null && uid > 0) return uid
        }
        val profileHtml = api.fetchProfilePage()
        if (profileHtml != null) {
            val uidFromProfile = Regex("""profileJSON\s*=\s*\{[^}]*"uid"\s*:\s*(\d+)""").find(profileHtml)
            if (uidFromProfile != null) uidFromProfile.groupValues[1].toLongOrNull()?.let { if (it > 0) return it }
        }
        val studios = fetchStudios()
        if (studios.isNotEmpty()) {
            studios.firstOrNull()?.owner?.uid?.let { if (it > 0) return it }
        }
        return null
    }

    suspend fun fetchCurrentUser(): CbartUserInfo? {
        val cred = currentCredential() ?: return null
        val uid = cred.userId ?: return null
        val profileInfo = fetchProfileInfo()
        val avatarUrl = profileInfo?.first ?: cred.avatarUrl
        val nickName = profileInfo?.second ?: cred.nickName ?: cred.userName ?: ""
        return CbartUserInfo(
            uid = uid, username = cred.userName ?: "",
            nickName = nickName, avatarUrl = avatarUrl,
        )
    }

    suspend fun validateSession(): Boolean = api.validateSession()

    suspend fun currentCredential(): CbartCredential? = credentialFlowRef.firstOrNull()

    // ==================== 视频详情 ====================

    suspend fun fetchVideoDetail(videoId: Long): CbartVideoDetailItem? {
        val html = api.fetchVideoDetailPage(videoId) ?: return null
        return parseVideoDetailFromHtml(html)
    }

    suspend fun addVideoComment(videoId: Long, content: String): Boolean {
        val response = api.addVideoComment(videoId, content)
        return response?.code == 200
    }
}

internal data class CbartUserInfo(
    val uid: String,
    val username: String,
    val nickName: String,
    val avatarUrl: String?,
)

/**
 * 从 /video/detail?id=xxx 页面 HTML 中提取 videoDetailJSON.list[0] 并解析
 */
internal fun parseVideoDetailFromHtml(html: String): CbartVideoDetailItem? {
    val listRegex = Regex("""videoDetailJSON\.list\s*=\s*(\[[^\]]+\])""")
    val listMatch = listRegex.find(html) ?: return null
    val rawJson = listMatch.groupValues[1]

    val fixed = rawJson
        .replace(Regex(",\\s*\\]"), "]")
        .replace(Regex("(?<![\\\"\\w])true(?![\\\"\\w])"), "true")
        .replace(Regex("(?<![\\\"\\w])false(?![\\\"\\w])"), "false")
        .replace(Regex("(?<![\\\"\\w])null(?![\\\"\\w])"), "null")

    return try {
        val list = apiJson.decodeFromString<List<CbartVideoDetailItem>>(fixed)
        list.firstOrNull()
    } catch (_: Exception) {
        null
    }
}
