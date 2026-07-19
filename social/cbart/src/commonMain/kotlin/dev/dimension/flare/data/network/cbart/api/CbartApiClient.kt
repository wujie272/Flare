package dev.dimension.flare.data.network.cbart.api

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.CbartCredential
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
) {
    private suspend fun credential(): CbartCredential? =
        credentialFlow.firstOrNull()

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

    suspend fun fetchHomePage(): String? {
        return try { httpClient().get(CBART_BASE).bodyAsText() } catch (_: Exception) { null }
    }

    suspend fun fetchProfilePage(): String? {
        return try { httpClient().get("$CBART_BASE/profile").bodyAsText() } catch (_: Exception) { null }
    }

    suspend fun fetchStudioListPage(): String? {
        return try { httpClient().get("$CBART_BASE/studio/list").bodyAsText() } catch (_: Exception) { null }
    }

    suspend fun validateSession(): Boolean {
        val result = articleList()
        return result?.code == 200
    }

    suspend fun verifyCaptcha(page: Int = 1, captchaToken: String): CbartSimpleResponse? = postForm(
        "/verify_captcha", mapOf("page" to page.toString(), "captcha_aliyun_token" to captchaToken),
    )

    // ==================== 收藏 ====================

    suspend fun favouriteContent(contentId: Long, contentType: String = "video"): CbartSimpleResponse? = postForm(
        "/favourite", mapOf("content_id" to contentId.toString(), "content_type" to contentType),
    )

    suspend fun unfavouriteContent(contentId: Long, contentType: String = "video"): CbartSimpleResponse? = postForm(
        "/unfavourite", mapOf("content_id" to contentId.toString(), "content_type" to contentType),
    )

    // ==================== 关注工作室 ====================

    suspend fun followStudio(studioId: Long): CbartSimpleResponse? = postForm(
        "/studio_follow", mapOf("studio_id" to studioId.toString()),
    )

    suspend fun unfollowStudio(studioId: Long): CbartSimpleResponse? = postForm(
        "/studio_unfollow", mapOf("studio_id" to studioId.toString()),
    )


    suspend fun fetchVideoDetailPage(videoId: Long): String? {
        return try {
            httpClient().get("$CBART_BASE/video/detail?id=$videoId").bodyAsText()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addVideoComment(videoId: Long, content: String): CbartVideoCommentAddResponse? = postForm(
        "/update_video_comment", mapOf("id" to videoId.toString(), "content" to content),
    )

    private inline fun <reified T> tryParse(text: String): T? {
        return try { json.decodeFromString<T>(text) } catch (_: Exception) { null }
    }
}
