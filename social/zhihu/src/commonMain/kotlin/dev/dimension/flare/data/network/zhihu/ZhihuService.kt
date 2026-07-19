package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.ZhihuCredential
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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

    suspend fun currentCredential(): ZhihuCredential? = credentialFlow.firstOrNull()
    
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

    private suspend fun signedClient(): HttpClient {
        val cookie = buildCookie()
        val currentDc0 = dc0()
        return ktorClient {
            install(ContentNegotiation) { json(JSON) }
            defaultRequest {
                header("User-Agent", ZHIHU_UA)
                header("Accept", "application/json, text/plain, */*")
                header("Accept-Language", "zh-CN,zh;q=0.9")
                header("Referer", "$ZHIHU_BASE/")
                if (cookie.isNotBlank()) header("Cookie", cookie)
                header("x-requested-with", "fetch")
                header("x-zse-93", ZSE93)
                header("x-zse-96", "2.0_${createZse96(ZSE93, url.buildString(), currentDc0)}")
            }
        }
    }

    private fun createZse96(zse93: String, fullUrl: String, dc0: String): String {
        val path = "/" + fullUrl.substringAfter("//").substringAfter('/')
        val signSource = listOfNotNull(zse93, path, dc0).joinToString("+")
        return ZhihuZseSigner.encryptZseV4(md5Hex(signSource))
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
            onCredentialRefreshed(refreshed)
            refreshed
        }
    }

    // ========== 热榜 ==========
    
    suspend fun fetchHotList(): List<ZhihuHotItem> {
        val cookie = buildCookie()
        val response = httpClient().get("$ZHIHU_API/v3/feed/topstory/hot-lists") {
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
                        hotValue = item["hot_value"]?.jsonPrimitive?.content ?: item["heat"]?.jsonPrimitive?.content ?: "0",
                        url = target["url"]?.jsonPrimitive?.content ?: "",
                        type = "question",
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ========== 知乎日报 ==========
    
    suspend fun fetchDailyStories(): List<ZhihuDailyStory> {
        return try {
            val response = httpClient().get("$ZHIHU_DAILY/stories/latest")
            val text = response.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            val stories = root["stories"]?.jsonArray ?: return emptyList()
            stories.mapNotNull { element ->
                val story = element.jsonObject
                try {
                    ZhihuDailyStory(
                        id = story["id"]?.jsonPrimitive?.content ?: "",
                        title = story["title"]?.jsonPrimitive?.content ?: "",
                        hint = story["hint"]?.jsonPrimitive?.content,
                        imageUrl = story["images"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                            ?: story["image"]?.jsonPrimitive?.content,
                        url = story["url"]?.jsonPrimitive?.content ?: "https://daily.zhihu.com/story/${story["id"]?.jsonPrimitive?.content}",
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ========== 推荐流（需要签名，需要 ensureSession） ==========
    
    suspend fun fetchRecommendFeed(): List<ZhihuFeedItem> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v3/feed/topstory/recommend") {
            header("desktop", "true")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            parseFeedItems(data)
        } catch (_: Exception) { emptyList() }
    }

    // ========== 搜索（需要签名，需要 ensureSession） ==========
    
    suspend fun search(query: String): List<ZhihuFeedItem> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/search_v3") {
            header("q", query); header("limit", "20")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            parseSearchItems(data)
        } catch (_: Exception) { emptyList() }
    }

    // ========== 用户信息（需要签名，需要 ensureSession） ==========

    suspend fun fetchCurrentUser(): ZhihuUserInfo? {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/me") {
            header("include", "id,name,url_token,avatar_url,user_type")
        }
        val text = response.bodyAsText()
        client.close()
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

    suspend fun fetchAnswerDetail(questionId: String, answerId: String): String? {
        val response = httpClient().get("$ZHIHU_BASE/question/$questionId/answer/$answerId")
        val text = response.bodyAsText()
        return if (text.contains("AnswerContent") || text.contains("answer-content")) text else null
    }

    suspend fun fetchArticleDetail(articleId: String): String? {
        val response = httpClient().get("https://zhuanlan.zhihu.com/p/$articleId")
        val text = response.bodyAsText()
        return if (text.contains("PostContent") || text.contains("article-content")) text else null
    }

    // ========== 互动操作（需要签名，需要 ensureSession） ==========

    suspend fun voteAnswer(answerId: String, voteType: String): Boolean {
        ensureSession()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/answers/$answerId/vote") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"$voteType"}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("voteup_count")
    }

    suspend fun voteArticle(articleId: String, voteType: String): Boolean {
        ensureSession()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/articles/$articleId/vote") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"$voteType"}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("voteup_count")
    }

    suspend fun bookmarkContent(contentType: String, contentId: String, add: Boolean): Boolean {
        ensureSession()
        val client = signedClient()
        val action = if (add) "add" else "remove"
        val response = client.post("https://api.zhihu.com/collections/contents/$contentType/$contentId") {
            header("x-requested-with", "fetch")
            setBody("""${action}_collections=default""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("collection_id") || !add
    }

    suspend fun followMember(memberId: String): Boolean {
        ensureSession()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/members/$memberId/followers") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("follower_count")
    }

    suspend fun unfollowMember(memberId: String): Boolean {
        ensureSession()
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/members/$memberId/followers") {
            contentType(ContentType.Application.Json)
            setBody("""{"_method":"DELETE"}""")
        }
        val text = response.bodyAsText()
        client.close()
        return text.contains("follower_count") || text.contains("is_following")
    }

    suspend fun fetchMemberRelation(memberId: String): JsonObject? {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$memberId") {
            header("include", "is_following,is_followed,follower_count,answer_count,articles_count")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) { null }
    }

    suspend fun fetchComments(contentType: String, contentId: String, page: Int = 1): List<ZhihuComment> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/$contentType/$contentId/comments") {
            header("limit", "20"); header("offset", "${(page - 1) * 20}")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { element ->
                val comment = element.jsonObject
                try {
                    ZhihuComment(
                        id = comment["id"]?.jsonPrimitive?.content ?: "",
                        content = comment["content"]?.jsonPrimitive?.content ?: "",
                        authorName = comment["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                        authorAvatar = comment["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                        likeCount = comment["like_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        createdAt = comment["created_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }


    // ========== 用户时间线（需要签名，需要 ensureSession） ==========

    /**
     * 获取用户回答列表
     * API: /api/v4/members/{id}/answers?offset={offset}&limit={limit}
     */
    suspend fun fetchUserAnswers(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuFeedItem> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$userId/answers") {
            header("offset", "$offset"); header("limit", "$limit")
        }
        val text = response.bodyAsText()
        client.close()
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
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$userId/articles") {
            header("offset", "$offset"); header("limit", "$limit")
        }
        val text = response.bodyAsText()
        client.close()
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
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$userId/pins") {
            header("offset", "$offset"); header("limit", "$limit")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            ZhihuPagingResponse(data = emptyList(), isEnd = isEnd) // 想法暂不解析，返回空
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    // ========== 关注/粉丝列表 ==========

    /**
     * 获取用户关注列表
     * API: /api/v4/members/{id}/followees?offset={offset}&limit={limit}
     */
    suspend fun fetchFollowees(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuPerson> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$userId/followees") {
            header("offset", "$offset"); header("limit", "$limit")
        }
        val text = response.bodyAsText()
        client.close()
        return parsePersonList(text)
    }

    /**
     * 获取用户粉丝列表
     * API: /api/v4/members/{id}/followers?offset={offset}&limit={limit}
     */
    suspend fun fetchFollowers(userId: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuPerson> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$userId/followers") {
            header("offset", "$offset"); header("limit", "$limit")
        }
        val text = response.bodyAsText()
        client.close()
        return parsePersonList(text)
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
     * API: /api/v4/notifications/v2?limit={limit}&offset={offset}
     */
    suspend fun fetchNotifications(offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuNotificationItem> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/notifications/v2") {
            header("limit", "$limit"); header("offset", "$offset")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return ZhihuPagingResponse(emptyList(), isEnd = true)
            val paging = root["paging"]?.jsonObject
            val isEnd = paging?.get("is_end")?.jsonPrimitive?.content?.toBoolean() ?: true
            ZhihuPagingResponse(
                data = data.mapNotNull { element ->
                    val obj = element.jsonObject
                    try {
                        val content = obj["content"]?.jsonObject
                        val actors = content?.get("actors")?.jsonArray
                        val actor = actors?.firstOrNull()?.jsonObject
                        val target = content?.get("target")?.jsonObject
                        val targetLink = target?.get("link")?.jsonPrimitive?.content ?: ""
                        val targetText = target?.get("text")?.jsonPrimitive?.content ?: ""
                        ZhihuNotificationItem(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            type = obj["type"]?.jsonPrimitive?.content ?: "",
                            isRead = obj["is_read"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                            createTime = obj["create_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                            verb = content?.get("verb")?.jsonPrimitive?.content ?: "",
                            actorName = actor?.get("name")?.jsonPrimitive?.content,
                            actorLink = actor?.get("link")?.jsonPrimitive?.content,
                            targetText = targetText,
                            targetLink = targetLink,
                            targetTitle = targetText,
                        )
                    } catch (_: Exception) { null }
                },
                isEnd = isEnd,
            )
        } catch (_: Exception) { ZhihuPagingResponse(emptyList(), isEnd = true) }
    }

    /**
     * 获取未读通知数量
     * API: /api/v4/me
     */
    suspend fun fetchNotificationBadgeCount(): Int {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/me") {
            header("include", "notification_count")
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            obj["notification_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
    }

    // ========== 用户搜索 ==========

    /**
     * 搜索用户
     * API: /api/v4/search_v3?t=people&q={query}&offset={offset}
     */
    suspend fun searchUsers(query: String, offset: Int = 0, limit: Int = 20): ZhihuPagingResponse<ZhihuPerson> {
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/search_v3") {
            header("t", "people"); header("q", query); header("limit", "$limit"); header("offset", "$offset")
        }
        val text = response.bodyAsText()
        client.close()
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
                        name = person["name"]?.jsonPrimitive?.content ?: "",
                        urlToken = person["url_token"]?.jsonPrimitive?.content,
                        avatarUrl = person["avatar_url"]?.jsonPrimitive?.content,
                        headline = person["headline"]?.jsonPrimitive?.content,
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
        ensureSession()
        val client = signedClient()
        val response = client.get("$ZHIHU_API/v4/members/$urlToken") {
            header("include", "id,name,url_token,avatar_url,headline,gender,follower_count,following_count,answer_count,articles_count,is_following,is_followed,user_type")
        }
        val text = response.bodyAsText()
        client.close()
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
        val client = signedClient()
        val response = client.post("$ZHIHU_API/v4/video/play_info") {
            header("r", videoId)
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
        ensureSession()
        val client = signedClient()
        val url = "$ZHIHU_API/v3/feed/topstory/recommend"
        val response = client.get(url) {
            header("desktop", "true")
            header("limit", "$limit")
            if (cursor != null) {
                header("cursor", cursor)
            }
        }
        val text = response.bodyAsText()
        client.close()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return Pair(emptyList(), null)
            val nextCursor = root["paging"]?.jsonObject?.get("next")?.jsonPrimitive?.content
            Pair(parseFeedItems(data), nextCursor)
        } catch (_: Exception) { Pair(emptyList(), null) }
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
                        )
                    }
                    type.contains("article") || targetType == "article" -> {
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
                            title = question?.get("title")?.jsonPrimitive?.content ?: "",
                            excerpt = objectType["excerpt"]?.jsonPrimitive?.content ?: "",
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
                            title = objectType["title"]?.jsonPrimitive?.content ?: "",
                            excerpt = objectType["excerpt"]?.jsonPrimitive?.content ?: "",
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
                            title = objectType["title"]?.jsonPrimitive?.content ?: "",
                            excerpt = objectType["description"]?.jsonPrimitive?.content ?: objectType["excerpt"]?.jsonPrimitive?.content ?: "",
                            url = objectType["url"]?.jsonPrimitive?.content ?: "",
                            authorName = objectType["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = objectType["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: objectType["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = objectType["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = objectType["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = objectType["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        )
                    }
                    type == "pin" || type == "moments" -> {
                        ZhihuFeedItem(
                            id = id, type = "pin",
                            title = objectType["excerpt_title"]?.jsonPrimitive?.content ?: "想法",
                            excerpt = objectType["excerpt_title"]?.jsonPrimitive?.content ?: "",
                            url = objectType["url"]?.jsonPrimitive?.content ?: "https://www.zhihu.com/pin/$id",
                            authorName = objectType["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content,
                            authorId = objectType["author"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: objectType["author"]?.jsonObject?.get("url_token")?.jsonPrimitive?.content,
                            authorAvatar = objectType["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content,
                            voteCount = objectType["like_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            commentCount = objectType["comment_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            createdAt = objectType["created"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        )
                    }
                    else -> null
                }
            } catch (_: Exception) { null }
        }
    }
}

// ========== 数据模型 ==========

internal data class ZhihuHotItem(
    val id: String,
    val title: String,
    val excerpt: String?,
    val answerCount: Int,
    val followerCount: Int,
    val hotValue: String,
    val url: String,
    val type: String,
)

internal data class ZhihuDailyStory(
    val id: String,
    val title: String,
    val hint: String?,
    val imageUrl: String?,
    val url: String,
)

internal data class ZhihuFeedItem(
    val id: String,
    val type: String, // "answer" or "article"
    val title: String,
    val excerpt: String,
    val url: String,
    val authorName: String?,
    val authorId: String?,
    val authorAvatar: String?,
    val voteCount: Int,
    val commentCount: Int,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

internal data class ZhihuComment(
    val id: String,
    val content: String,
    val authorName: String?,
    val authorAvatar: String?,
    val likeCount: Int,
    val createdAt: Long,
)

internal data class ZhihuUserInfo(
    val id: String,
    val name: String,
    val urlToken: String?,
    val avatarUrl: String?,
)

/**
 * 知乎用户资料（用于关注/粉丝列表等）
 */
internal data class ZhihuPerson(
    val id: String,
    val name: String,
    val urlToken: String? = null,
    val avatarUrl: String? = null,
    val headline: String? = null,
    val gender: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val answerCount: Int = 0,
    val articlesCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
    val userType: String? = null,
)

/**
 * 知乎通知项
 */
internal data class ZhihuNotificationItem(
    val id: String,
    val type: String,
    val isRead: Boolean = false,
    val createTime: Long = 0,
    val verb: String = "",
    val actorName: String? = null,
    val actorLink: String? = null,
    val targetText: String? = null,
    val targetLink: String? = null,
    val targetTitle: String? = null,
    val targetType: String? = null,
)

/**
 * 视频播放信息
 */
internal data class ZhihuVideoInfo(
    val videoId: String,
    val url: String? = null,
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 通用分页响应
 */
internal data class ZhihuPagingResponse<T>(
    val data: List<T>,
    val isEnd: Boolean = true,
    val nextOffset: Int? = null,
    val total: Int = 0,
)
