package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.data.network.cbart.api.*
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull


internal class CbartService(
    credentialFlow: Flow<CbartCredential>,
    onCredentialRefreshed: suspend (CbartCredential) -> Unit = {},
) {
    val api = CbartApiClient(credentialFlow = credentialFlow, onCredentialRefreshed = onCredentialRefreshed)
    private val credentialFlowRef = credentialFlow

    suspend fun fetchArticles(page: Int = 1, limit: Int = 20): List<CbartArticleItem> {
        val response = api.articleList(page = page, limit = limit, news = 1)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchLatestResources(page: Int = 1, limit: Int = 50): List<CbartNewContentItem> {
        val response = api.newContentList(page = page, limit = limit)
        val items = response?.data?.newContent ?: emptyList()
        // get_new_content API 不返回 owner 信息，需要并发补全
        if (items.any { it.owner == null && it.uid != null }) {
            return kotlinx.coroutines.coroutineScope {
                items.map { item ->
                    async {
                        if (item.owner == null && item.uid != null) {
                            val owner = fetchUserByUid(item.uid.toString())
                            if (owner != null) item.copy(owner = owner) else item
                        } else item
                    }
                }.map { it.await() }
            }
        }
        return items
    }

    suspend fun fetchMyContent(page: Int = 1): List<CbartContentItem> {
        val uid = currentUid() ?: return emptyList()
        val response = api.contentList(uid = uid, page = page)
        return response?.data?.contents ?: emptyList()
    }

    suspend fun fetchUserContent(uid: String, page: Int = 1): List<CbartContentItem> {
        val response = api.contentList(uid = uid, page = page, getOwner = 1)
        return response?.data?.contents ?: emptyList()
    }

    /** uid -> owner 缓存 */
    private val ownerCache = mutableMapOf<String, CbartVideoOwner>()

    /**
     * 根据 uid 获取用户信息（昵称+头像）
     * 优先调 content_list?uid=xxx&get_owner=1 精准获取，
     * 不行再调 video_list 取少量 owner 数据填充缓存。
     */
    suspend fun fetchUserByUid(uid: String): CbartVideoOwner? {
        ownerCache[uid]?.let { return it }
        // 方案1：精准查该用户的内容，取 owner
        val contentResult = runCatching {
            api.contentList(uid = uid, page = 1, limit = 1, getOwner = 1)
                ?.data?.contents?.firstOrNull()?.owner
        }.getOrNull()
        if (contentResult != null) {
            ownerCache[uid] = contentResult
            return contentResult
        }
        // 方案2：video_list 兜底，只拉少量数据
        val videoResult = runCatching {
            api.videoList(page = 1, limit = 5)
        }.getOrNull()
        videoResult?.data?.contents?.forEach { content ->
            content.owner?.let { owner ->
                ownerCache[content.uid.toString()] = owner
            }
        }
        return ownerCache[uid]
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

    // ==================== 收藏/取消收藏（toggle） ====================

    /**
     * 收藏/取消收藏视频，返回 true 表示收藏成功，false 表示取消收藏
     */
    suspend fun toggleVideoFav(videoId: Long): Boolean? {
        val response = api.toggleVideoFav(videoId)
        return when (response?.data?.update) {
            "+" -> true
            "-" -> false
            else -> null
        }
    }

    // ==================== 关注/取消关注（toggle） ====================

    suspend fun toggleFollow(fromUid: Long, toUid: Long, follow: Boolean): Boolean {
        val action = if (follow) "add" else "remove"
        val response = api.toggleFollow(fromUid, toUid, action)
        return response?.code == 200
    }

    suspend fun isStudioFollowed(studioId: Long): Boolean {
        val response = api.studioList(keyword = "", page = 1, limit = 1, needOwner = 1, needStudio = 1, contentNum = 0, filterQuery = studioId.toString())
        return response?.data?.contents?.firstOrNull()?.isFollowed == true
    }

    // ==================== 用户信息 ====================

    suspend fun fetchUsernameFromHomePage(): Pair<String, String>? {
        val html = api.fetchHomePage() ?: return null
        // 方案1：navi_user_panel 中的用户名
        val regex1 = Regex("""navi_user_panel[^>]*>\s*([^<]+?)\s*<span""", RegexOption.DOT_MATCHES_ALL)
        val match1 = regex1.find(html)
        if (match1 != null) {
            val username = match1.groupValues[1].trim()
            if (username.isNotBlank()) {
                return Pair(username, username)
            }
        }
        // 方案2：meJSON 中的 username
        val regex2 = Regex("""meJSON\s*=\s*\{[^}]*?username\s*:\s*['"]([^'"]+)['"].*?\}""")
        val match2 = regex2.find(html)
        if (match2 != null) {
            val username = match2.groupValues[1].trim()
            if (username.isNotBlank()) {
                return Pair(username, username)
            }
        }
        return null
    }

    suspend fun fetchProfileInfo(): Pair<String?, String?>? {
        val html = api.fetchProfilePage() ?: return null
        // 方案1：meJSON 中的 avatar_url 和 nick_name
        val avatarRegex1 = Regex("""avatar_url"\s*:\s*"([^"]+)"""")
        val avatarUrl1 = avatarRegex1.find(html)?.groupValues?.get(1)?.trim()?.replace("\\/", "/")
        val nickRegex1 = Regex("""nick_name"\s*:\s*"([^"]+)"""")
        val nickName1 = nickRegex1.find(html)?.groupValues?.get(1)?.trim()
        // 方案2：meJSON 中的 avatar 相对路径 + display_name
        val avatarRegex2 = Regex("""avatar"\s*:\s*"([^"]+)"""")
        val avatarUrl2 = avatarRegex2.find(html)?.groupValues?.get(1)?.trim()
            ?.let { "https://www.tpzf001.com$it" }
        val nickRegex2 = Regex("""display_name"\s*:\s*"([^"]+)"""")
        val nickName2 = nickRegex2.find(html)?.groupValues?.get(1)?.trim()
        // 取第一个非空值
        val avatarUrl = avatarUrl1 ?: avatarUrl2
        val nickName = nickName1 ?: nickName2
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
        // 方案1：meJSON.uid = xxx（页面可能直接赋值）
        val meUid1 = Regex("""meJSON\.uid\s*=\s*(\d+)""").find(html)
        if (meUid1 != null) {
            val uid = meUid1.groupValues[1].toLongOrNull()
            if (uid != null && uid > 0) return uid
        }
        // 方案2：meJSON = {... uid:xxx ...}（对象格式）
        val meUid2 = Regex("""meJSON\s*=\s*\{[^}]*?uid\s*:\s*(\d+)""").find(html)
        if (meUid2 != null) {
            val uid = meUid2.groupValues[1].toLongOrNull()
            if (uid != null && uid > 0) return uid
        }
        // 方案2：从 profile 页面 JSON 中取 uid
        val profileHtml = api.fetchProfilePage()
        if (profileHtml != null) {
            val uidFromProfile = Regex("""profileJSON\s*=\s*\{[^}]*"uid"\s*:\s*(\d+)""").find(profileHtml)
            if (uidFromProfile != null) uidFromProfile.groupValues[1].toLongOrNull()?.let { if (it > 0) return it }
        }
        // 方案3：从 article_list 响应中取当前用户的 uid
        // article_list 已由 validateSession() 调用过，这里直接复用
        val articles = try {
            fetchArticles(page = 1, limit = 5)
        } catch (_: Exception) {
            emptyList()
        }
        if (articles.isNotEmpty()) {
            // 文章列表中的 uid 是作者 id，取第一个非零 uid
            articles.firstOrNull()?.uid?.let { if (it > 0) return it }
        }
        // 方案4：从 video_list 响应中取 owner uid
        val videos = try {
            fetchVideos(page = 1, limit = 5)
        } catch (_: Exception) {
            emptyList()
        }
        if (videos.isNotEmpty()) {
            videos.firstOrNull()?.owner?.uid?.let { if (it > 0) return it }
        }
        // 方案5：从 studio 列表取 owner uid（超时已包 try-catch）
        val studios = try {
            fetchStudios()
        } catch (_: Exception) {
            emptyList()
        }
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
