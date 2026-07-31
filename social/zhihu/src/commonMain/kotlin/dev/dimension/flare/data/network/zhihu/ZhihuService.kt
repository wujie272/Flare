package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.ZhihuCredential
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private const val ZHIHU_BASE = "https://www.zhihu.com"
private const val ZHIHU_API = "https://www.zhihu.com/api"
private const val ZHIHU_DAILY = "https://news-at.zhihu.com/api/4"
private const val ZHIHU_DAILY_FALLBACK = "https://daily.zhihu.com/api/4"
private const val ZHIHU_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val ZSE93 = "101_3_3.0"
private val cookieRefreshInterval = 12.hours

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 知乎数据服务层
 * 支持：热榜、日报、推荐流、搜索、内容详情、互动操作、Cookie自动刷新
 */
internal class ZhihuService(
    private val credentialFlow: Flow<ZhihuCredential>,
    private val onCredentialRefreshed: suspend (ZhihuCredential) -> Unit = {},
) {
    private val refreshMutex = Mutex()
    /** 缓存 credential，避免每次读 Flow (参考 CbartApiClient) */
    private var cachedCredential: ZhihuCredential? = null
    /** 缓存 UUID → urlToken 映射，避免重复 API 调用 */
    private val urlTokenCache = mutableMapOf<String, String>()

    suspend fun currentCredential(): ZhihuCredential? {
        if (cachedCredential == null) {
            cachedCredential = credentialFlow.firstOrNull()
        }
        return cachedCredential
    }

    /**
     * 解析 userId(UUID) 为 urlToken（如 wujie-81）
     * 当前用户直接走 credential 缓存，非当前用户调用 API 获取
     */
    /**
     * 检查当前 session 是否有效
     * 调用 /api/v4/me 检查返回值，如果返回 null 说明 session 过期
     */
    suspend fun checkSessionValid(): Boolean {
        return try {
            fetchCurrentUser() != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resolveUrlToken(userId: String): String? {
        // 当前用户：走 credential 缓存
        currentCredential()?.let { cred ->
            if (userId == cred.userId) {
                return cred.urlToken ?: fetchMemberByUrlToken(userId)?.urlToken
            }
        }
        // 非当前用户：查缓存或 API
        urlTokenCache[userId]?.let { return it }
        val urlToken = fetchMemberByUrlToken(userId)?.urlToken
        if (urlToken != null) urlTokenCache[userId] = urlToken
        return urlToken
    }
    
    private suspend fun buildCookie(): String {
        currentCredential()?.let { cred ->
            cred.rawCookie?.let { return it }
            return buildString {
                cred.zc0?.let { append("z_c0=$it; ") }
                cred.dc0?.let { append("d_c0=$it; ") }
                cred.xsrfToken?.let { append("xsrf=$it; ") }
            }
        }
        return ""
    }

    private suspend fun dc0(): String = currentCredential()?.dc0 ?: ""

    private fun httpClient() = ktorClient {
        defaultRequest {
            header("User-Agent", ZHIHU_UA)
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "zh-CN,zh;q=0.9")
            header("Referer", "$ZHIHU_BASE/")
        }
    }

    /**
     * 创建带 Cookie 和 zse-93 的 HTTP 客户端（不含 zse-96 签名）
     * 签名在 get/post 时通过 [signZse96] 单独计算，确保 URL 正确
     */
    private suspend fun signedClient(): HttpClient {
        val cookie = buildCookie()
        return ktorClient {
            defaultRequest {
                header("User-Agent", ZHIHU_UA)
                header("Accept", "application/json, text/plain, */*")
                header("Accept-Language", "zh-CN,zh;q=0.9")
                header("Referer", "$ZHIHU_BASE/")
                if (cookie.isNotBlank()) header("Cookie", cookie)
                header("x-requested-with", "fetch")
                header("x-zse-93", ZSE93)
            }
        }
    }

    /**
     * 计算 zse-96 签名并设置到请求头
     * 使用原始 URL 字符串（而非 url.buildString()）避免 Ktor 编码差异
     * dc0 为空时不签名，参考 zhihu-plus-plus 的 signZhihuFetchRequest
     */
    private fun HttpRequestBuilder.signZse96(rawUrl: String, dc0: String) {
        if (dc0.isBlank()) return  // dc0 为空时跳过签名
        val path = "/" + rawUrl.substringAfter("//").substringAfter('/')
        val signSource = buildString {
            append(ZSE93)
            append('+')
            append(path)
            append('+')
            append(dc0)
        }
        val signature = ZhihuZseSigner.encryptZseV4(md5Hex(signSource))
        header("x-zse-96", "2.0_$signature")
    }

    /**
     * 发送带签名的 GET 请求，遇 401 自动刷新 Cookie 后重试一次
     */
    private suspend fun signedGetText(url: String): String {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        try {
            val response = client.get(url) {
                signZse96(url, currentDc0)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                client.close()
                refreshSession()
                val retryClient = signedClient()
                try {
                    return retryClient.get(url) {
                        signZse96(url, currentDc0)
                    }.bodyAsText()
                } finally {
                    retryClient.close()
                }
            }
            return response.bodyAsText()
        } finally {
            client.close()
        }
    }

    /**
     * 发送带签名的 POST 请求，遇 401 自动刷新 Cookie 后重试一次
     */
    private suspend fun signedPostText(url: String, body: String? = null): String {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        try {
            val response = client.post(url) {
                signZse96(url, currentDc0)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                client.close()
                refreshSession()
                val retryClient = signedClient()
                try {
                    return retryClient.post(url) {
                        signZse96(url, currentDc0)
                        if (body != null) {
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }
                    }.bodyAsText()
                } finally {
                    retryClient.close()
                }
            }
            return response.bodyAsText()
        } finally {
            client.close()
        }
    }

    // ========== Cookie 自动刷新 ==========

    /**
     * 每次需要签名的 API 调用前检查 Cookie 是否需要刷新
     * 热榜和日报不需要签名，不需要刷新
     */
    private suspend fun ensureSession() {
        if (shouldRefreshSession()) {
            refreshSession()
        }
    }

    private suspend fun shouldRefreshSession(): Boolean {
        val cred = currentCredential() ?: return false
        val lastRefresh = cred.lastCookieRefreshEpochMillis ?: return false
        val now = Clock.System.now().toEpochMilliseconds()
        return now - lastRefresh > cookieRefreshInterval.inWholeMilliseconds
    }

    /**
     * 刷新 Cookie：访问首页，合并 Set-Cookie
     * 知乎的 z_c0 是长期有效的，但 d_c0 和 xsrf 可能会变
     */
    private suspend fun refreshSession(): ZhihuCredential? {
        return refreshMutex.withLock {
            val cred = currentCredential() ?: return@withLock null
            val now = Clock.System.now().toEpochMilliseconds()

            // 双检锁：检查是否已经被其他协程刷新了
            val current = currentCredential() ?: return@withLock null
            val lastRefresh = current.lastCookieRefreshEpochMillis
            if (lastRefresh != null && now - lastRefresh < cookieRefreshInterval.inWholeMilliseconds) {
                return@withLock current
            }

            val cookie = buildCookie()
            val response = ktorClient {
                defaultRequest {
                    header("User-Agent", ZHIHU_UA)
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header("Accept-Language", "zh-CN,zh;q=0.9")
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                }
            }.get(ZHIHU_BASE)

            val setCookieHeaders = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            if (setCookieHeaders.isEmpty()) {
                // 没变化，只更新时间戳
                val refreshed = cred.copy(lastCookieRefreshEpochMillis = now)
                cachedCredential = refreshed
                onCredentialRefreshed(refreshed)
                return@withLock refreshed
            }

            var newZc0 = cred.zc0
            var newDc0 = cred.dc0
            var newXsrf = cred.xsrfToken
            var changed = false

            for (setCookie in setCookieHeaders) {
                val nameValue = setCookie.substringBefore(';').trim()
                val eqIdx = nameValue.indexOf('=')
                if (eqIdx <= 0) continue
                val name = nameValue.substring(0, eqIdx).trim()
                val value = nameValue.substring(eqIdx + 1).trim()
                if (value.isBlank()) continue

                when (name) {
                    "z_c0" -> if (value != newZc0) { newZc0 = value; changed = true }
                    "d_c0" -> if (value != newDc0) { newDc0 = value; changed = true }
                    "xsrf" -> if (value != newXsrf) { newXsrf = value; changed = true }
                }
            }

            if (!changed) {
                val refreshed = cred.copy(lastCookieRefreshEpochMillis = now)
                cachedCredential = refreshed
                onCredentialRefreshed(refreshed)
                return@withLock refreshed
            }

            // 重新构建 rawCookie
            val rawCookie = buildString {
                newZc0?.let { append("z_c0=$it; ") }
                newDc0?.let { append("d_c0=$it; ") }
                newXsrf?.let { append("xsrf=$it; ") }
            }.trimEnd(' ', ';')

            val refreshed = cred.copy(
                zc0 = newZc0,
                dc0 = newDc0,
                xsrfToken = newXsrf,
                rawCookie = rawCookie.takeIf { it.isNotBlank() },
                lastCookieRefreshEpochMillis = now,
            )
            cachedCredential = refreshed
            onCredentialRefreshed(refreshed)
            refreshed
        }
    }

    // ========== 热榜 ==========
    
    /**
     * 获取热榜
     * API: /api/v3/feed/topstory/hot-lists/total?limit=50
     */
    suspend fun fetchHotList(): List<ZhihuHotItem> {
        val cookie = buildCookie()
        val response = httpClient().get("$ZHIHU_API/v3/feed/topstory/hot-lists/total?limit=50") {
            header("Cookie", cookie)
        }
        val text = response.bodyAsText()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { element ->
                val item = element.jsonObject
                val target = item["target"]?.jsonObject ?: return@mapNotNull null
                try {
                    ZhihuHotItem(
                        id = target["id"]?.jsonPrimitive?.content ?: "",
                        title = target["title"]?.jsonPrimitive?.content ?: "",
                        excerpt = target["excerpt"]?.jsonPrimitive?.content,
                        answerCount = target["answer_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        followerCount = target["follower_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        hotValue = item["detail_text"]?.jsonPrimitive?.content ?: "0",
                        url = "https://www.zhihu.com/question/${target["id"]?.jsonPrimitive?.content ?: ""}",
                        type = "question",
                        thumbnail = item["children"]?.jsonArray?.firstOrNull()?.jsonObject?.get("thumbnail")?.jsonPrimitive?.content,
                        createdAt = target["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ========== 知乎日报 ==========

    /**
     * 获取日报故事的原始内容链接
     * 调 /api/7/story/{id} 解析 HTML 中的 <a class="originUrl">
     * 如果获取失败或没有原始链接，返回 null（走系统浏览器兜底）
     */
    suspend fun fetchDailyStoryOriginalUrl(storyId: String): String? {
        return try {
            val response = httpClient().get("https://daily.zhihu.com/api/7/story/$storyId")
            val html = response.bodyAsText()
            // 从 HTML 中提取 originUrl
            val regex = Regex("""<a\\s+class="originUrl"\\s+href="([^"]+)""")
            regex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
    
    suspend fun fetchDailyStories(): List<ZhihuDailyStory> {
        return try {
            val response = httpClient().get("$ZHIHU_DAILY/stories/latest")
            val text = response.bodyAsText()
            parseDailyStories(text)
        } catch (_: Exception) {
            // 主 API 失败时尝试备用 DNS
            try {
                val fallbackResponse = httpClient().get("$ZHIHU_DAILY_FALLBACK/stories/latest")
                parseDailyStories(fallbackResponse.bodyAsText())
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 获取某天之前的日报
     * API: /stories/before/{date}
     * date 格式: yyyyMMdd
     */
    suspend fun fetchDailyStoriesBefore(date: String): Pair<String, List<ZhihuDailyStory>> {
        return try {
            val response = httpClient().get("$ZHIHU_DAILY/stories/before/$date")
            parseDailyStoriesWithDate(response.bodyAsText())
        } catch (_: Exception) {
            try {
                val fallbackResponse = httpClient().get("$ZHIHU_DAILY_FALLBACK/stories/before/$date")
                parseDailyStoriesWithDate(fallbackResponse.bodyAsText())
            } catch (_: Exception) {
                Pair(date, emptyList())
            }
        }
    }

    private fun parseDailyStories(text: String): List<ZhihuDailyStory> {
        return parseDailyStoriesWithDate(text).second
    }

    private fun parseDailyStoriesWithDate(text: String): Pair<String, List<ZhihuDailyStory>> {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val stories = root["stories"]?.jsonArray ?: return Pair("", emptyList())
            val dateStr = root["date"]?.jsonPrimitive?.content ?: ""
            val items = stories.mapNotNull { element ->
                val story = element.jsonObject
                try {
                    ZhihuDailyStory(
                        id = story["id"]?.jsonPrimitive?.content ?: "",
                        title = story["title"]?.jsonPrimitive?.content ?: "",
                        hint = story["hint"]?.jsonPrimitive?.content,
                        imageUrl = story["images"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                            ?: story["image"]?.jsonPrimitive?.content,
                        url = story["url"]?.jsonPrimitive?.content ?: "https://daily.zhihu.com/story/${story["id"]?.jsonPrimitive?.content}",
                        date = dateStr,
                    )
                } catch (_: Exception) { null }
            }
            Pair(dateStr, items)
        } catch (_: Exception) { Pair("", emptyList()) }
    }

    // ========== 推荐流（需要签名，需要 ensureSession） ==========
    
    // ========== 搜索（需要签名，需要 ensureSession） ==========
    
    suspend fun search(query: String, offset: Int = 0): List<ZhihuFeedItem> {
        val text = signedGetText("$ZHIHU_API/v4/search_v3?q=${query.encodeURLParam()}&limit=20&offset=$offset")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            val items = parseSearchItems(data)
            // 异步获取视频播放地址
            items.map { item ->
                if (item.type == "video" && item.videoId != null) {
                    val videoInfo = try {
                        fetchVideoPlayInfo(item.videoId, item.id, "answer")
                    } catch (_: Exception) { null }
                    if (videoInfo?.url != null) {
                        item.copy(
                            videoPlayUrl = videoInfo.url,
                            videoWidth = videoInfo.width,
                            videoHeight = videoInfo.height,
                        )
                    } else item
                } else item
            }
        } catch (_: Exception) { emptyList() }
    }

    // ========== 用户信息（需要签名，需要 ensureSession） ==========

    suspend fun fetchCurrentUser(): ZhihuUserInfo? {
        // /api/v4/me 不需要 zse-96 签名，参考 zhihu-plus-plus 的 fetchVerifiedZhihuAccount
        val cookie = buildCookie()
        val text = try {
            httpClient().get("$ZHIHU_API/v4/me?include=id,name,url_token,avatar_url,user_type") {
                header("Cookie", cookie)
            }.bodyAsText()
        } catch (_: Exception) { return null }
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return null
            ZhihuUserInfo(
                id = id,
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                urlToken = obj["url_token"]?.jsonPrimitive?.content,
                avatarUrl = obj["avatar_url"]?.jsonPrimitive?.content,
            )
        } catch (_: Exception) { null }
    }

    // ========== 内容详情 ==========

    /**
     * 获取回答详情（含正文 HTML）
     * API: /api/v4/answers/{id}?include=...
     */
    suspend fun fetchAnswerDetail(id: String): JsonObject? {
        val include = "content,excerpt,voteup_count,comment_count,reaction,ip_info,attachment,question.title,question.id,author.name,author.url_token,author.avatar_url,author.headline,created_time,updated_time,author.badge_v2"
        val text = signedGetText("$ZHIHU_API/v4/answers/$id?include=$include")
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) { null }
    }

    /**
     * 获取文章详情（含正文 HTML）
     * API: /api/v4/articles/{id}?include=...
     */
    suspend fun fetchArticleDetail(id: String): JsonObject? {
        val include = "content,excerpt,voteup_count,comment_count,reaction,ip_info,attachment,author.name,author.url_token,author.avatar_url,author.headline,created,updated,author.badge_v2"
        val text = signedGetText("$ZHIHU_API/v4/articles/$id?include=$include")
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) { null }
    }

    // ========== 互动操作（需要签名，需要 ensureSession） ==========

    suspend fun submitComment(contentType: String, contentId: String, content: String, replyCommentId: String? = null): Boolean {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val url = "$ZHIHU_API/v4/comment_v5/${contentType}s/$contentId/comment"
        val escapedText = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val bodyObj = buildJsonObject {
            put("content", "<p>$escapedText</p>")
            replyCommentId?.let { put("reply_comment_id", it) }
        }
        val response = client.post(url) {
            signZse96(url, currentDc0)
            contentType(ContentType.Application.Json)
            setBody(bodyObj.toString())
        }
        val text = response.bodyAsText()
        client.close()
        return response.status.isSuccess()
    }

    suspend fun voteAnswer(answerId: String, voteType: String): Boolean {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val url = "$ZHIHU_API/v4/answers/$answerId/vote"
        val response = client.post(url) {
            signZse96(url, currentDc0)
            contentType(ContentType.Application.Json)
            setBody("""{"type":"$voteType"}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("voteup_count")
    }

    suspend fun voteArticle(articleId: String, voteType: String): Boolean {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val url = "$ZHIHU_API/v4/articles/$articleId/vote"
        val response = client.post(url) {
            signZse96(url, currentDc0)
            contentType(ContentType.Application.Json)
            setBody("""{"type":"$voteType"}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("voteup_count")
    }

    suspend fun bookmarkContent(contentType: String, contentId: String, add: Boolean): Boolean {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val action = if (add) "add" else "remove"
        val response = client.post("https://api.zhihu.com/collections/contents/$contentType/$contentId") {
            signZse96("https://api.zhihu.com/collections/contents/$contentType/$contentId", currentDc0)
            header("x-requested-with", "fetch")
            setBody("""${action}_collections=default""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("collection_id") || !add
    }

    suspend fun followMember(memberId: String): Boolean {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/members/$memberId/followers") {
            signZse96("$ZHIHU_API/v4/members/$memberId/followers", currentDc0)
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("follower_count")
    }

    suspend fun unfollowMember(memberId: String): Boolean {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/members/$memberId/followers") {
            signZse96("$ZHIHU_API/v4/members/$memberId/followers", currentDc0)
            contentType(ContentType.Application.Json)
            setBody("""{"_method":"DELETE"}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("follower_count") || text.contains("is_following")
    }

    suspend fun fetchMemberRelation(memberId: String): JsonObject? {
        val text = signedGetText("$ZHIHU_API/v4/members/$memberId?include=is_following,is_followed,follower_count,answer_count,articles_count")
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) { null }
    }

    /**
     * 获取子评论（楼中楼）
     * API: /api/v4/comment_v5/{contentType}s/{contentId}/comments?parent_id={parentId}&page={page}
     */
    suspend fun fetchChildComments(contentType: String, contentId: String, parentId: String, page: Int = 1): ZhihuPagingResponse<ZhihuComment> {
        val text = signedGetText("$ZHIHU_API/v4/comment_v5/${contentType}s/$contentId/comments?parent_id=$parentId&limit=20&offset=${(page - 1) * 20}")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val comments = data.mapNotNull { element ->
                val comment = element.jsonObject
                try {
                    val authorMember = comment["author"]?.jsonObject?.get("member")?.jsonObject
                    ZhihuComment(
                        id = comment["id"]?.jsonPrimitive?.content ?: "",
                        content = comment["content"]?.jsonPrimitive?.content ?: "",
                        authorName = authorMember?.get("name")?.jsonPrimitive?.content,
                        authorId = authorMember?.get("id")?.jsonPrimitive?.content ?: authorMember?.get("url_token")?.jsonPrimitive?.content,
                        authorAvatar = authorMember?.get("avatar_url")?.jsonPrimitive?.content,
                        likeCount = comment["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        createdAt = comment["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        replyToAuthorName = comment["reply_to_author"]?.jsonObject?.get("member")?.jsonObject?.get("name")?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) { null }
            }
            ZhihuPagingResponse(data = comments, isEnd = isEnd)
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    suspend fun fetchComments(contentType: String, contentId: String, page: Int = 1): ZhihuPagingResponse<ZhihuComment> {
        val text = signedGetText("$ZHIHU_API/v4/$contentType/$contentId/comments?limit=20&offset=${(page - 1) * 20}")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val total = paging?.get("totals")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val comments = data.mapNotNull { element ->
                val comment = element.jsonObject
                try {
                    val authorMember = comment["author"]?.jsonObject?.get("member")?.jsonObject
                    ZhihuComment(
                        id = comment["id"]?.jsonPrimitive?.content ?: "",
                        content = comment["content"]?.jsonPrimitive?.content ?: "",
                        authorName = authorMember?.get("name")?.jsonPrimitive?.content,
                        authorId = authorMember?.get("id")?.jsonPrimitive?.content ?: authorMember?.get("url_token")?.jsonPrimitive?.content,
                        authorAvatar = authorMember?.get("avatar_url")?.jsonPrimitive?.content,
                        likeCount = comment["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        createdAt = comment["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        replyToAuthorName = comment["reply_to_author"]?.jsonObject?.get("member")?.jsonObject?.get("name")?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) { null }
            }
            ZhihuPagingResponse(data = comments, isEnd = isEnd, total = total)
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }


    // ========== 用户时间线（需要签名，需要 ensureSession） ==========

    /**
     * 获取用户回答列表
     * API: /api/v4/members/{id}/answers?offset={offset}&limit={limit}
     */
    suspend fun fetchUserAnswers(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuFeedItem> {
        val text = signedGetText("$ZHIHU_API/v4/members/$userId/answers?offset=$offset&limit=$limit")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val nextOffset = try {
                val next = paging?.get("next")?.jsonPrimitive?.content ?: ""
                if (next.contains("offset=")) {
                    next.substringAfter("offset=").substringBefore("&").toIntOrNull()
                } else null
            } catch (_: Exception) { null }
            ZhihuPagingResponse(
                data = data.mapNotNull { element ->
                    val obj = element.jsonObject
                    try {
                        val question = obj["question"]?.jsonObject
                        val author = obj["author"]?.jsonObject
                        ZhihuFeedItem(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            type = "answer",
                            title = question?.get("title")?.jsonPrimitive?.content ?: "",
                            excerpt = obj["excerpt"]?.jsonPrimitive?.content ?: obj["content"]?.jsonPrimitive?.content?.take(200) ?: "",
                            url = "https://www.zhihu.com/question/${question?.get("id")?.jsonPrimitive?.content}/answer/${obj["id"]?.jsonPrimitive?.content}",
                            authorName = author?.get("name")?.jsonPrimitive?.content,
                            authorId = author?.get("id")?.jsonPrimitive?.content ?: author?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = author?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = obj["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = obj["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = obj["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = obj["updated_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    } catch (_: Exception) { null }
                },
                isEnd = isEnd,
                nextOffset = nextOffset,
            )
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    /**
     * 获取用户文章列表
     * API: /api/v4/members/{id}/articles?offset={offset}&limit={limit}
     */
    suspend fun fetchUserArticles(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuFeedItem> {
        val text = signedGetText("$ZHIHU_API/v4/members/$userId/articles?offset=$offset&limit=$limit")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            ZhihuPagingResponse(
                data = data.mapNotNull { element ->
                    val obj = element.jsonObject
                    try {
                        val author = obj["author"]?.jsonObject
                        ZhihuFeedItem(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            type = "article",
                            title = obj["title"]?.jsonPrimitive?.content ?: "",
                            excerpt = obj["excerpt"]?.jsonPrimitive?.content ?: obj["content"]?.jsonPrimitive?.content?.take(200) ?: "",
                            url = "https://zhuanlan.zhihu.com/p/${obj["id"]?.jsonPrimitive?.content}",
                            authorName = author?.get("name")?.jsonPrimitive?.content,
                            authorId = author?.get("id")?.jsonPrimitive?.content ?: author?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = author?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = obj["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = obj["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = obj["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = obj["updated_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    } catch (_: Exception) { null }
                },
                isEnd = isEnd,
            )
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    /**
     * 获取用户想法列表
     * API: /api/v4/members/{id}/pins?offset={offset}&limit={limit}
     */
    suspend fun fetchUserPins(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuFeedItem> {
        val text = signedGetText("$ZHIHU_API/v4/members/$userId/pins?offset=$offset&limit=$limit")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val nextUrl = paging?.get("next")?.jsonPrimitive?.content
            val nextOffset = nextUrl?.let {
                it.substringAfter("offset=", "").substringBefore("&").substringBefore("\u0026").toIntOrNull()
            }
            ZhihuPagingResponse(
                data = data.mapNotNull { element ->
                    val obj = element.jsonObject
                    try {
                        val author = obj["author"]?.jsonObject
                        // 从 content 数组中提取文本作为 excerpt
                        val contentArray = obj["content"]?.jsonArray
                        val excerpt = contentArray?.joinToString(" ") { item ->
                            item.jsonObject["content"]?.jsonPrimitive?.content.orEmpty()
                        }?.take(200) ?: obj["excerpt_title"]?.jsonPrimitive?.content ?: ""
                        ZhihuFeedItem(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            type = "pin",
                            title = obj["excerpt_title"]?.jsonPrimitive?.content ?: "想法",
                            excerpt = excerpt,
                            url = "https://www.zhihu.com${obj["url"]?.jsonPrimitive?.content ?: "/pins/${obj["id"]?.jsonPrimitive?.content}"}",
                            authorName = author?.get("name")?.jsonPrimitive?.content,
                            authorId = author?.get("id")?.jsonPrimitive?.content ?: author?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = author?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = obj["like_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: obj["reaction_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = obj["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = obj["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = obj["updated"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    } catch (_: Exception) { null }
                },
                isEnd = isEnd,
                nextOffset = nextOffset,
            )
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    // ========== 关注/粉丝列表 ==========

    /**
     * 获取用户粉丝列表
     * API: https://api.zhihu.com/people/{id}/followers?offset={offset}&limit={limit}
     * api.zhihu.com 域名直接接受 UUID，不需要 urlToken 解析（参考 zhihu-plus-plus）
     */
    suspend fun fetchFollowers(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuPerson> {
        val cookie = buildCookie()
        val text = httpClient().get("https://api.zhihu.com/people/$userId/followers?offset=$offset&limit=$limit") {
            header("Cookie", cookie)
        }.bodyAsText()
        return parsePersonList(text)
    }

    /**
     * 获取用户关注列表
     * API: /api/v4/members/{urlToken}/followees?offset={offset}&limit={limit}
     * www.zhihu.com 域名只认 urlToken，需要 resolveUrlToken 转换
     */
    suspend fun fetchFollowees(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuPerson> {
        val urlToken = resolveUrlToken(userId) ?: userId
        val text = signedGetText("$ZHIHU_API/v4/members/$urlToken/followees?offset=$offset&limit=$limit")
        return parsePersonList(text)
    }

    // ========== 收藏夹 ==========

    /**
     * 获取用户的收藏夹列表
     * API: /api/v4/people/{urlToken}/collections
     */
    suspend fun fetchCollections(urlToken: String): List<ZhihuCollection> {
        val text = signedGetText("$ZHIHU_API/v4/people/$urlToken/collections?limit=20")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { element ->
                val obj = element.jsonObject
                try {
                    ZhihuCollection(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        title = obj["title"]?.jsonPrimitive?.content ?: "",
                        itemCount = obj["item_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        isDefault = obj["is_default"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 获取收藏夹中的内容
     * API: /api/v4/collections/{collectionId}/items
     * 返回的 content 字段结构与推荐流/搜索结果的 ZhihuFeedItem 一致
     */
    suspend fun fetchCollectionItems(collectionId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuFeedItem> {
        val text = signedGetText("$ZHIHU_API/v4/collections/$collectionId/items?limit=$limit&offset=$offset")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val items = data.mapNotNull { element ->
                val obj = element.jsonObject
                try {
                    val content = obj["content"]?.jsonObject ?: return@mapNotNull null
                    val target = content["target"]?.jsonObject ?: content
                    val targetType = target["type"]?.jsonPrimitive?.content ?: ""
                    val id = target["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    // 支持 answer 和 article 两种类型
                    if (targetType == "answer") {
                        val question = target["question"]?.jsonObject
                        ZhihuFeedItem(
                            id = id, type = "answer",
                            title = question?.get("title")?.jsonPrimitive?.content ?: "",
                            excerpt = target["excerpt"]?.jsonPrimitive?.content ?: target["content"]?.jsonPrimitive?.content?.take(200) ?: "",
                            url = "https://www.zhihu.com/question/${question?.get("id")?.jsonPrimitive?.content}/answer/$id",
                            authorName = target["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = target["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: target["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = target["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = target["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = target["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    } else if (targetType == "article") {
                        ZhihuFeedItem(
                            id = id, type = "article",
                            title = target["title"]?.jsonPrimitive?.content ?: "",
                            excerpt = target["excerpt"]?.jsonPrimitive?.content ?: target["content"]?.jsonPrimitive?.content?.take(200) ?: "",
                            url = "https://zhuanlan.zhihu.com/p/$id",
                            authorName = target["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = target["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: target["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = target["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = target["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = target["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    } else null
                } catch (_: Exception) { null }
            }
            ZhihuPagingResponse(data = items, isEnd = isEnd)
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    private fun parsePersonList(text: String): ZhihuPagingResponse<ZhihuPerson> {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            ZhihuPagingResponse(
                data = data.mapNotNull { element ->
                    val obj = element.jsonObject
                    try {
                        ZhihuPerson(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            urlToken = obj["url_token"]?.jsonPrimitive?.content,
                            avatarUrl = obj["avatar_url"]?.jsonPrimitive?.content,
                            headline = obj["headline"]?.jsonPrimitive?.content,
                            gender = obj["gender"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            followerCount = obj["follower_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            followingCount = obj["following_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            answerCount = obj["answer_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            articlesCount = obj["articles_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            isFollowing = obj["is_following"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                            isFollowed = obj["is_followed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                            userType = obj["user_type"]?.jsonPrimitive?.content,
                        )
                    } catch (_: Exception) { null }
                },
                isEnd = isEnd,
            )
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    // ========== 通知 ==========

    /**
     * 获取通知列表
     * API: https://api.zhihu.com/notifications/v3/timeline/entry/comment?limit={limit}
     */
    suspend fun fetchNotifications(offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuNotificationItem> {
        val url = if (offset > 0) {
            "https://api.zhihu.com/notifications/v3/timeline/entry/comment?limit=$limit&offset=$offset"
        } else {
            "https://api.zhihu.com/notifications/v3/timeline/entry/comment?limit=$limit"
        }
        val text = signedGetText(url)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val nextUrl = paging?.get("next")?.jsonPrimitive?.content
            val nextOffset = nextUrl?.let {
                val raw = it.substringAfter("offset=", "").substringBefore("&").substringBefore("\u0026")
                raw.toIntOrNull()
            }
            val items = data.mapNotNull { element ->
                val obj = element.jsonObject
                try {
                    if (obj["type"]?.jsonPrimitive?.content == "empty") return@mapNotNull null
                    val content = obj["content"]?.jsonObject
                    val head = obj["head"]?.jsonObject
                    val targetSource = obj["target_source"]?.jsonObject
                    ZhihuNotificationItem(
                        id = obj["id"]?.jsonPrimitive?.content ?: obj["unique_id"]?.jsonPrimitive?.content ?: "",
                        type = obj["card_type"]?.jsonPrimitive?.content ?: "",
                        isRead = obj["is_read"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        createTime = obj["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        verb = content?.get("sub_title")?.jsonPrimitive?.content ?: "",
                        actorName = content?.get("title")?.jsonPrimitive?.content,
                        actorLink = head?.get("target_link")?.jsonPrimitive?.content,
                        targetText = content?.get("text")?.jsonPrimitive?.content,
                        targetLink = content?.get("target_link")?.jsonPrimitive?.content,
                        targetTitle = targetSource?.get("text")?.jsonPrimitive?.content ?: content?.get("text")?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) { null }
            }
            ZhihuPagingResponse(data = items, isEnd = isEnd, nextOffset = nextOffset)
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    /**
     * 获取未读通知数量
     * API: /api/v4/me
     */
    /**
     * 获取各分类通知的未读数量
     * comment: default_notifications_count（评论/回复）
     * like: vote_thank_notifications_count（赞同/感谢）
     * mention: follow_notifications_count（关注/粉丝）
     */
    suspend fun fetchNotificationBadgeCounts(): Map<NotificationFilter, Int> {
        val text = signedGetText("$ZHIHU_API/v4/me?include=default_notifications_count,follow_notifications_count,vote_thank_notifications_count")
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val comment = obj["default_notifications_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val follow = obj["follow_notifications_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val vote = obj["vote_thank_notifications_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            mapOf(
                NotificationFilter.Comment to comment,
                NotificationFilter.Like to vote,
                NotificationFilter.Mention to follow,
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchNotificationBadgeCount(): Int {
        val text = signedGetText("$ZHIHU_API/v4/me?include=default_notifications_count,follow_notifications_count,vote_thank_notifications_count")
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val comment = obj["default_notifications_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val follow = obj["follow_notifications_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val vote = obj["vote_thank_notifications_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            comment + follow + vote
        } catch (_: Exception) { 0 }
    }

    // ========== 用户搜索 ==========

    /**
     * 搜索用户
     * API: /api/v4/search_v3?t=people&q={query}&offset={offset}
     */
    suspend fun searchUsers(query: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuPerson> {
        val text = signedGetText("$ZHIHU_API/v4/search_v3?t=people&q=${query.encodeURLParam()}&limit=$limit&offset=$offset")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            val people = data.mapNotNull { element ->
                val obj = element.jsonObject
                try {
                    val person = obj["object"]?.jsonObject ?: obj
                    ZhihuPerson(
                        id = person["id"]?.jsonPrimitive?.content ?: "",
                        name = (person["name"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                        urlToken = person["url_token"]?.jsonPrimitive?.content,
                        avatarUrl = person["avatar_url"]?.jsonPrimitive?.content,
                        headline = (person["headline"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                        followerCount = person["follower_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        isFollowing = person["is_following"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        userType = person["user_type"]?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) { null }
            }
            ZhihuPagingResponse(data = people, isEnd = isEnd)
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    // ========== 用户资料 ==========

    /**
     * 通过 url_token 获取用户资料
     * API: /api/v4/members/{urlToken}?include=...
     */
    suspend fun fetchMemberByUrlToken(urlToken: String): ZhihuPerson? {
        val text = signedGetText("$ZHIHU_API/v4/members/$urlToken?include=id,name,url_token,avatar_url,headline,gender,follower_count,following_count,answer_count,articles_count,is_following,is_followed,user_type")
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            ZhihuPerson(
                id = obj["id"]?.jsonPrimitive?.content ?: "",
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                urlToken = obj["url_token"]?.jsonPrimitive?.content,
                avatarUrl = obj["avatar_url"]?.jsonPrimitive?.content,
                headline = obj["headline"]?.jsonPrimitive?.content,
                gender = obj["gender"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                followerCount = obj["follower_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                followingCount = obj["following_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                answerCount = obj["answer_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                articlesCount = obj["articles_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                isFollowing = obj["is_following"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                isFollowed = obj["is_followed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                userType = obj["user_type"]?.jsonPrimitive?.content,
            )
        } catch (_: Exception) { null }
    }

    // ========== 视频播放信息 ==========

    /**
     * 获取视频播放地址
     * API: /api/v4/video/play_info?r={videoId}
     */
    suspend fun fetchVideoPlayInfo(videoId: String, contentId: String, contentType: String = "answer"): ZhihuVideoInfo? {
        ensureSession()
        val currentDc0 = dc0()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/video/play_info?r=$videoId") {
            signZse96("$ZHIHU_API/v4/video/play_info?r=$videoId", currentDc0)
            contentType(ContentType.Application.Json)
            setBody("""{"content_id":"$contentId","content_type_str":"$contentType","video_id":"$videoId","scene_code":"answer_detail_web","is_only_video":true}""")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val playlist = root["video_play"]?.jsonObject?.get("playlist")?.jsonObject
            val mp4List = playlist?.get("mp4")?.jsonArray
            var bestUrl: String? = null
            var bestBitrate = 0
            var bestWidth = 0
            var bestHeight = 0
            mp4List?.forEach { element ->
                val video = element.jsonObject
                val bitrate = video["bitrate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestUrl = video["url"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    bestWidth = video["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    bestHeight = video["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                }
            }
            if (bestUrl != null) {
                ZhihuVideoInfo(videoId = videoId, url = bestUrl, bitrate = bestBitrate, width = bestWidth, height = bestHeight)
            } else null
        } catch (_: Exception) { null }
    }

    // ========== 推荐流分页 ==========

    /**
     * 带光标分页的推荐流
     * API: /api/v3/feed/topstory/recommend?cursor={cursor}
     */
    suspend fun fetchRecommendFeedWithCursor(cursor: String? = null, limit: Int = 20): Pair<List<ZhihuFeedItem>, String?> {
        val url = buildString {
            append("$ZHIHU_API/v3/feed/topstory/recommend?limit=$limit")
            if (cursor != null) {
                append("&cursor=$cursor")
            }
        }
        val text = signedGetText(url)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return Pair(emptyList(), null)
            val nextCursor = root["paging"]?.jsonObject?.get("next")?.jsonPrimitive?.content
            val items = parseFeedItems(data)
            // 异步获取视频播放地址
            val enriched = items.map { item ->
                if (item.type == "video" && item.videoId != null) {
                    val videoInfo = try {
                        fetchVideoPlayInfo(item.videoId, item.id, "answer")
                    } catch (_: Exception) { null }
                    if (videoInfo?.url != null) {
                        item.copy(
                            videoPlayUrl = videoInfo.url,
                            videoWidth = videoInfo.width,
                            videoHeight = videoInfo.height,
                        )
                    } else item
                } else item
            }
            Pair(enriched, nextCursor)
        } catch (_: Exception) { Pair(emptyList(), null) }
    }

    // ========== 关注动态 ==========

    /**
     * 获取关注动态（按时间排序）
     * API: /api/v3/moments?offset={offset}&limit={limit}
     */
    suspend fun fetchMoments(offset: String? = null, limit: Int = 20): ZhihuMomentsResponse {
        val url = buildString {
            append("https://www.zhihu.com/api/v3/moments?limit=$limit")
            if (offset != null) {
                append("&offset=$offset")
            }
        }
        val text = signedGetText(url)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuMomentsResponse(emptyList(), null, true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            // 从 next URL 中提取 offset 作为下一页游标
            val nextUrl = paging?.get("next")?.jsonPrimitive?.content
            val nextOffset = nextUrl?.let {
                val raw = it.substringAfter("offset=", "").substringBefore("&").substringBefore("&")
                raw.takeIf { it.isNotBlank() }
            }
            val items = parseMomentsItems(data)
            ZhihuMomentsResponse(data = items, nextOffset = nextOffset, isEnd = isEnd)
        } catch (_: Exception) { ZhihuMomentsResponse(emptyList(), null, true) }
    }

    private fun parseMomentsItems(data: JsonArray): List<ZhihuMomentsItem> {
        val result = mutableListOf<ZhihuMomentsItem>()
        for (element in data) {
            val obj = element.jsonObject
            try {
                val type = obj["type"]?.jsonPrimitive?.content ?: ""
                when (type) {
                    "feed_advert" -> {} // 跳过广告
                    "feed_group" -> {
                        // 展开分组
                        val list = obj["list"]?.jsonArray ?: continue
                        for (sub in list) {
                            parseSingleMoment(sub.jsonObject)?.let { result.add(it) }
                        }
                    }
                    "feed" -> {
                        parseSingleMoment(obj)?.let { result.add(it) }
                    }
                }
            } catch (_: Exception) {}
        }
        return result
    }

    private fun parseSingleMoment(obj: JsonObject): ZhihuMomentsItem? {
        return try {
            val verb = obj["verb"]?.jsonPrimitive?.content ?: ""
            val target = obj["target"]?.jsonObject ?: return null
            val actors = obj["actors"]?.jsonArray
            val actor = actors?.firstOrNull()?.jsonObject
            val actorName = actor?.get("name")?.jsonPrimitive?.content ?: ""
            val actorUrlToken = actor?.get("url_token")?.jsonPrimitive?.content
            val actorAvatar = actor?.get("avatar_url")?.jsonPrimitive?.content
            val createdTime = obj["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
            val targetType = target["type"]?.jsonPrimitive?.content ?: ""
            val targetId = target["id"]?.jsonPrimitive?.content ?: ""
            val question = target["question"]?.jsonObject
            
            // 构建标题/摘要
            val title = when (targetType) {
                "answer" -> question?.get("title")?.jsonPrimitive?.content ?: ""
                "article" -> target["title"]?.jsonPrimitive?.content ?: ""
                "question" -> target["title"]?.jsonPrimitive?.content ?: ""
                "pin" -> target["excerpt_title"]?.jsonPrimitive?.content ?: ""
                else -> ""
            }
            val excerpt = target["excerpt"]?.jsonPrimitive?.content
                ?: target["excerpt_new"]?.jsonPrimitive?.content
                ?: ""

            // 构建目标 URL
            val url = when (targetType) {
                "answer" -> {
                    val qid = question?.get("id")?.jsonPrimitive?.content ?: ""
                    "https://www.zhihu.com/question/$qid/answer/$targetId"
                }
                "article" -> "https://zhuanlan.zhihu.com/p/$targetId"
                "question" -> "https://www.zhihu.com/question/$targetId"
                "pin" -> target["url"]?.jsonPrimitive?.content ?: "https://www.zhihu.com/pin/$targetId"
                else -> ""
            }

            ZhihuMomentsItem(
                verb = verb,
                actorName = actorName,
                actorUrlToken = actorUrlToken,
                actorAvatar = actorAvatar,
                targetType = targetType,
                targetId = targetId,
                title = title,
                excerpt = excerpt,
                url = url,
                createdAt = createdTime,
            )
        } catch (_: Exception) { null }
    }


    // ========== 数据解析辅助 ==========

    private fun parseFeedItems(data: JsonArray): List<ZhihuFeedItem> {
        return data.mapNotNull { element ->
            val item = element.jsonObject
            try {
                val target = item["target"]?.jsonObject ?: return@mapNotNull null
                val targetType = target["type"]?.jsonPrimitive?.content ?: ""
                val feedType = item["feed_type"]?.jsonPrimitive?.content ?: ""
                val type = feedType.ifEmpty { targetType }
                val id = target["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                when {
                    type.contains("answer") || targetType == "answer" -> {
                        val question = target["question"]?.jsonObject
                        val childThumb = item["children"]?.jsonArray?.firstOrNull()?.jsonObject?.get("thumbnail")?.jsonPrimitive?.content
                        ZhihuFeedItem(
                            id = id, type = "answer", title = question?.get("title")?.jsonPrimitive?.content ?: "",
                            excerpt = target["excerpt"]?.jsonPrimitive?.content ?: target["content"]?.jsonPrimitive?.content?.take(200) ?: "",
                            url = "https://www.zhihu.com/question/${question?.get("id")?.jsonPrimitive?.content}/answer/$id",
                            authorName = target["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = target["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: target["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = target["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = target["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = target["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = target["updated_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            thumbnail = childThumb,
                        )
                    }
                    type.contains("article") || targetType == "article" -> {
                        val childThumb = item["children"]?.jsonArray?.firstOrNull()?.jsonObject?.get("thumbnail")?.jsonPrimitive?.content
                        ZhihuFeedItem(
                            id = id, type = "article", title = target["title"]?.jsonPrimitive?.content ?: "",
                            excerpt = target["excerpt"]?.jsonPrimitive?.content ?: target["content"]?.jsonPrimitive?.content?.take(200) ?: "",
                            url = "https://zhuanlan.zhihu.com/p/$id",
                            authorName = target["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = target["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: target["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = target["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = target["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = target["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = target["updated_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            thumbnail = childThumb,
                        )
                    }
                    type.contains("zvideo") || targetType == "zvideo" -> {
                        ZhihuFeedItem(
                            id = id, type = "video",
                            title = target["title"]?.jsonPrimitive?.content ?: "",
                            excerpt = target["description"]?.jsonPrimitive?.content ?: target["excerpt"]?.jsonPrimitive?.content ?: "",
                            url = target["url"]?.jsonPrimitive?.content ?: "",
                            authorName = target["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = target["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: target["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = target["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = target["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: target["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = (target["created_time"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: target["created"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: item["created_time"]?.jsonPrimitive?.content?.toLongOrNull()) ?: 0,
                            videoCover = target["thumbnail"]?.jsonPrimitive?.content ?: target["excerpt"]?.jsonPrimitive?.content,
                            videoId = id,
                        )
                    }
                    type.contains("pin") || targetType == "pin" -> {
                        ZhihuFeedItem(
                            id = id, type = "pin",
                            title = target["excerpt_title"]?.jsonPrimitive?.content ?: "想法",
                            excerpt = target["excerpt_title"]?.jsonPrimitive?.content ?: "",
                            url = target["url"]?.jsonPrimitive?.content ?: "https://www.zhihu.com/pin/$id",
                            authorName = target["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = target["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: target["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = target["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = target["like_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: target["reaction_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = target["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = target["updated"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    }
                    type.contains("question") || targetType == "question" -> {
                        ZhihuFeedItem(
                            id = id, type = "question",
                            title = target["title"]?.jsonPrimitive?.content ?: target["name"]?.jsonPrimitive?.content ?: "",
                            excerpt = target["detail"]?.jsonPrimitive?.content ?: target["excerpt"]?.jsonPrimitive?.content ?: "",
                            url = target["url"]?.jsonPrimitive?.content ?: "https://www.zhihu.com/question/$id",
                            authorName = null,
                            authorId = null,
                            authorAvatar = null,
                            voteCount = target["follower_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = target["answer_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = target["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    }
                    else -> null
                }
            } catch (_: Exception) { null }
        }
    }

    /** 清理搜索 API 返回的 <em> 高亮标签 */
    private fun String.stripHighlight(): String = replace("<em>", "").replace("</em>", "")

    private fun parseSearchItems(data: JsonArray): List<ZhihuFeedItem> {
        return data.mapNotNull { element ->
            val item = element.jsonObject
            try {
                val objectType = item["object"]?.jsonObject
                val type = objectType?.get("type")?.jsonPrimitive?.content ?: ""
                val id = objectType?.get("id")?.jsonPrimitive?.content ?: return@mapNotNull null
                when {
                    type == "answer" -> {
                        val question = objectType["question"]?.jsonObject
                        ZhihuFeedItem(
                            id = id, type = "answer",
                            title = (question?.get("name")?.jsonPrimitive?.content ?: "").stripHighlight(),
                            excerpt = (objectType["excerpt"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                            url = "https://www.zhihu.com/question/${question?.get("id")?.jsonPrimitive?.content}/answer/$id",
                            authorName = objectType["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = objectType["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: objectType["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = objectType["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = objectType["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = objectType["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = objectType["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = objectType["updated_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    }
                    type == "article" -> {
                        ZhihuFeedItem(
                            id = id, type = "article",
                            title = (objectType["title"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                            excerpt = (objectType["excerpt"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                            url = "https://zhuanlan.zhihu.com/p/$id",
                            authorName = objectType["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = objectType["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: objectType["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = objectType["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = objectType["voteup_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = objectType["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = objectType["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            updatedAt = objectType["updated_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    }
                    type == "zvideo" -> {
                        ZhihuFeedItem(
                            id = id, type = "video",
                            title = (objectType["title"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                            excerpt = (objectType["description"]?.jsonPrimitive?.content ?: objectType["excerpt"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                            url = objectType["url"]?.jsonPrimitive?.content ?: "",
                            authorName = objectType["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = objectType["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: objectType["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = objectType["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = objectType["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = objectType["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = (objectType["created_time"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: objectType["created"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: item["created_time"]?.jsonPrimitive?.content?.toLongOrNull()) ?: 0,
                            videoCover = objectType["thumbnail"]?.jsonPrimitive?.content,
                            videoId = id,
                        )
                    }
                    type == "pin" || type == "moments" -> {
                        ZhihuFeedItem(
                            id = id, type = "pin",
                            title = (objectType["excerpt_title"]?.jsonPrimitive?.content ?: "想法").stripHighlight(),
                            excerpt = (objectType["excerpt_title"]?.jsonPrimitive?.content ?: "").stripHighlight(),
                            url = objectType["url"]?.jsonPrimitive?.content ?: "https://www.zhihu.com/pin/$id",
                            authorName = objectType["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = objectType["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: objectType["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = objectType["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = objectType["like_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = objectType["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = objectType["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    }
                    type == "ai_zhida" -> null // 跳过 AI 搜索摘要
                    else -> null
                }
            } catch (_: Exception) { null }
        }
    }
}


/**
 * URL 编码查询参数值
 */
private fun String.encodeURLParam(): String = this.encodeURLParameter()

// ========== 数据模型 ==========
