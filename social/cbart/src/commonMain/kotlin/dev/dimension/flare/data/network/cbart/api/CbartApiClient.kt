package dev.dimension.flare.data.network.cbart.api

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.CbartCredential
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

private const val CBART_API_BASE = "https://www.linzijiang.app/api"
private const val CBART_BASE = "https://www.linzijiang.app"
private const val CBART_CDN = "https://www.tpzf001.com"
private const val CBART_USER_AGENT = "Mozilla/5.0 (Android 16; Mobile; rv:152.0) Gecko/152.0 Firefox/152.0"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

internal class CbartApiClient(
    private val credentialFlow: Flow<CbartCredential>,
    private val onCredentialRefreshed: suspend (CbartCredential) -> Unit = {},
) {
    private var currentCredential: CbartCredential? = null

    private suspend fun credential(): CbartCredential? {
        if (currentCredential == null) {
            currentCredential = credentialFlow.firstOrNull()
        }
        return currentCredential
    }

    private val sessionRegex = Regex("""laravel_session=([^;]+)""")
    private val xsrfTokenRegex = Regex("""XSRF-TOKEN=([^;]+)""")

    /**
     * 从 Set-Cookie 头中提取新的会话凭证并更新
     */
    private suspend fun updateCredentialsFromHeaders(setCookieHeaders: List<String>) {
        var newSession: String? = null
        var newXsrfToken: String? = null
        for (header in setCookieHeaders) {
            sessionRegex.find(header)?.let { newSession = it.groupValues[1] }
            xsrfTokenRegex.find(header)?.let { newXsrfToken = it.groupValues[1] }
        }
        val session = newSession ?: return
        val old = currentCredential ?: return
        val updated = old.copy(
            laravelSession = session,
            xsrfToken = newXsrfToken ?: old.xsrfToken,
        )
        currentCredential = updated
        onCredentialRefreshed(updated)
    }

    private suspend fun buildCookie(): String? {
        val cred = credential() ?: return null
        val parts = mutableListOf("laravel_session=${cred.laravelSession}")
        cred.xsrfToken?.let { parts.add("XSRF-TOKEN=$it") }
        return parts.joinToString("; ")
    }

    private suspend fun httpClient(): HttpClient {
        val cookie = buildCookie()
        val cred = credential()
        return ktorClient {
            // Laravel 每次响应都会 Set-Cookie 刷新 laravel_session 和 XSRF-TOKEN
            // 如果不捕获更新，一直用旧 session 发请求，24h 后服务端回收旧 session 导致 419
            // 这个拦截器在每次请求后提取新 cookie，更新内存凭证并持久化
            install(HttpSend) {
                intercept { request ->
                    val call = execute(request)
                    val setCookieHeaders = call.response.headers.getAll(HttpHeaders.SetCookie)
                    if (!setCookieHeaders.isNullOrEmpty()) {
                        updateCredentialsFromHeaders(setCookieHeaders)
                    }
                    call
                }
            }
            defaultRequest {
                headers {
                    append("User-Agent", CBART_USER_AGENT)
                    append("Accept", "application/json, text/javascript, */*; q=0.01")
                    append("Accept-Language", "zh-CN,en;q=0.9")
                    append("X-Requested-With", "XMLHttpRequest")
                    append("Origin", CBART_BASE)
                    append("Referer", "$CBART_BASE/")
                    cookie?.let { append("Cookie", it) }
                    cred?.xsrfToken?.let { append("X-XSRF-TOKEN", it.decodeURLPart()) }
                }
            }
        }
    }

    private suspend inline fun <reified T> postForm(
        path: String,
        body: Map<String, String> = emptyMap(),
    ): T? {
        val text = httpClient().post("$CBART_API_BASE$path") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body.map { (k, v) -> "$k=$v" }.joinToString("&"))
        }.bodyAsText()
        return tryParse(text)
    }

    suspend fun contentList(uid: String, page: Int = 1, limit: Int = 20): CbartContentListResponse? = postForm(
        "/content_list", mapOf("limit" to limit.toString(), "page" to page.toString(), "uid" to uid),
    )

    suspend fun studioList(
        keyword: String = "", page: Int = 1, limit: Int = 20,
        needOwner: Int = 1, needStudio: Int = 1, contentNum: Int = 1, order: String = "updatetime",
        filterQuery: String? = null,
        followedByUid: Long? = null,
    ): CbartStudioListResponse? = postForm("/studio_list", buildMap {
        put("keyword", keyword); put("limit", limit.toString()); put("page", page.toString())
        put("need_owner", needOwner.toString()); put("need_studio", needStudio.toString())
        put("content_num", contentNum.toString()); put("order", order)
        filterQuery?.let { put("filter_query", it) }
        followedByUid?.let { put("followed_by_uid", it.toString()) }
        if (followedByUid != null) { put("check_followed", "1") }
    })

    suspend fun blogList(
        uid: String, page: Int = 1, limit: Int = 20,
        includeLowerTier: Int = 1, needPublicNum: Int = 1, getContentNum: Int = 1,
    ): CbartBlogListResponse? = postForm("/blog_list", mapOf(
        "uid" to uid, "page" to page.toString(), "limit" to limit.toString(),
        "include_lower_tier_blog" to includeLowerTier.toString(),
        "need_public_num" to needPublicNum.toString(), "get_content_num" to getContentNum.toString(),
    ))

    suspend fun articleList(page: Int = 1, limit: Int = 20, news: Int = 1): CbartArticleListResponse? = postForm(
        "/article_list", mapOf("limit" to limit.toString(), "page" to page.toString(), "news" to news.toString()),
    )

    suspend fun tierList(id: String, page: Int = 1): CbartTierListResponse? = postForm(
        "/tier_list", mapOf("id" to id, "page" to page.toString()),
    )

    suspend fun videoList(page: Int = 1, limit: Int = 20, uid: String? = null, purchasedVideo: Boolean = false): CbartVideoListResponse? = postForm(
        "/video_list", buildMap {
            put("limit", limit.toString()); put("page", page.toString())
            uid?.let { put("uid", it) }
            if (purchasedVideo) put("purchased_video", "1")
        },
    )

    suspend fun messageUserList(page: Int = 1, limit: Int = 19, shortContent: Int = 100, order: String = ""): CbartMessageListResponse? = postForm(
        "/message_user_list", mapOf("limit" to limit.toString(), "page" to page.toString(), "short_content" to shortContent.toString(), "order" to order),
    )

    // ==================== 最新内容 ====================

    suspend fun newContentList(page: Int = 1, limit: Int = 50): CbartNewContentListResponse? = postForm(
        "/get_new_content", mapOf("page" to page.toString(), "limit" to limit.toString()),
    )

    // ==================== 页面抓取 ====================

    suspend fun fetchHomePage(): String? {
        return try { httpClient().get(CBART_BASE).bodyAsText() } catch (_: Exception) { null }
    }

    suspend fun fetchProfilePage(): String? {
        return try { httpClient().get("$CBART_BASE/profile").bodyAsText() } catch (_: Exception) { null }
    }

    suspend fun fetchStudioListPage(): String? {
        return try { httpClient().get("$CBART_BASE/studio/list").bodyAsText() } catch (_: Exception) { null }
    }

    suspend fun fetchVideoDetailPage(videoId: Long): String? {
        return try {
            httpClient().get("$CBART_BASE/video/detail?id=$videoId").bodyAsText()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun validateSession(): Boolean {
        val result = articleList()
        return result?.code == 200
    }

    suspend fun verifyCaptcha(page: Int = 1, captchaToken: String): CbartSimpleResponse? = postForm(
        "/verify_captcha", mapOf("page" to page.toString(), "captcha_aliyun_token" to captchaToken),
    )

    // ==================== 收藏（toggle） ====================

    /**
     * 收藏/取消收藏 toggle，JS 源码实际调用 update_video_fav
     * 返回 {"data": {"update": "+"}} 表示收藏，"-" 表示取消
     */
    suspend fun toggleVideoFav(videoId: Long): CbartVideoFavResponse? = postForm(
        "/update_video_fav", mapOf("video_id" to videoId.toString()),
    )

    // ==================== 关注（toggle） ====================

    /**
     * 关注/取消关注用户，JS 源码实际调用 update_follow
     * action: "add" 关注, "remove" 取消
     */
    suspend fun toggleFollow(fromUid: Long, toUid: Long, action: String): CbartFollowResponse? = postForm(
        "/update_follow", mapOf("from_uid" to fromUid.toString(), "to_uid" to toUid.toString(), "action" to action),
    )

    // ==================== 视频评论 ====================

    suspend fun addVideoComment(videoId: Long, content: String): CbartVideoCommentAddResponse? = postForm(
        "/update_video_comment", mapOf("id" to videoId.toString(), "content" to content),
    )

    private inline fun <reified T> tryParse(text: String): T? {
        return try { json.decodeFromString<T>(text) } catch (_: Exception) { null }
    }
}
