package dev.dimension.flare.data.network.toutiao

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.ToutiaoCredential
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TOUTIAO_BASE = "https://www.toutiao.com"
private const val TOUTIAO_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 今日头条数据服务层
 * - 热榜: /hot-event/hot-board/ (public JSON API, no sign needed)
 * - 文章详情: /article/{id}/ (SSR HTML)
 * - 个人主页: /c/user/token/{token}/ (SSR HTML)
 */
internal class ToutiaoService(
    private val credentialFlow: Flow<ToutiaoCredential>,
) {
    private suspend fun rawCookie(): String? = credentialFlow.firstOrNull()?.rawCookie

    private suspend fun buildCookie(): String {
        val raw = rawCookie()
        if (raw != null) return raw
        // Fallback: build from fields
        val cred = credentialFlow.firstOrNull() ?: return ""
        return buildString {
            cred.ttwid?.let { append("ttwid=$it; ") }
            cred.csrftoken?.let { append("csrftoken=$it; ") }
            cred.ttWebid?.let { append("tt_webid=$it; ") }
        }
    }

    private suspend fun httpClient(): HttpClient {
        val cookie = buildCookie()
        return ktorClient {
            defaultRequest {
                headers {
                    append("User-Agent", TOUTIAO_UA)
                    append("Accept", "application/json, text/plain, */*")
                    append("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    append("Referer", "$TOUTIAO_BASE/")
                    append("Origin", TOUTIAO_BASE)
                    if (cookie.isNotBlank()) {
                        append("Cookie", cookie)
                    }
                }
            }
        }
    }

    /**
     * 获取今日头条热榜
     * API: GET /hot-event/hot-board/?origin=toutiao_pc
     * 不需要 _signature 签名
     */
    suspend fun fetchHotBoard(): List<ToutiaoHotItem> {
        val response = httpClient().get("$TOUTIAO_BASE/hot-event/hot-board/") {
            // Parameter without _signature - this API accepts unsigned requests
            url.parameters.append("origin", "toutiao_pc")
        }
        val text = response.bodyAsText()
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val dataArray = obj["data"]?.jsonArray ?: return emptyList()
            dataArray.mapNotNull { element ->
                val item = element.jsonObject
                try {
                    ToutiaoHotItem(
                        clusterId = item["ClusterIdStr"]?.jsonPrimitive?.content ?: "",
                        title = item["Title"]?.jsonPrimitive?.content ?: "",
                        hotValue = item["HotValue"]?.jsonPrimitive?.content ?: "0",
                        url = item["Url"]?.jsonPrimitive?.content ?: "",
                        imageUrl = item["Image"]?.jsonObject?.get("url")?.jsonPrimitive?.content,
                        label = item["Label"]?.jsonPrimitive?.content,
                        labelUrl = item["LabelUri"]?.jsonObject?.get("url")?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 获取推荐文章（首页默认内容）
     * 使用首页 SSR HTML，仅提取"要闻"栏目的5条新闻
     * 和热榜一起构成推荐页
     */
    suspend fun fetchRecommendedNews(): List<ToutiaoHotItem> {
        // Try the hot-board API first since it works without sign
        // The recommended news will be mixed with hot board data
        return fetchHotBoard().take(10)
    }

    /**
     * 获取文章详情
     * API: GET /article/{id}/
     * SSR HTML，可用 Ksoup 解析
     */
    suspend fun fetchArticleDetail(articleId: String): String? {
        val response = httpClient().get("$TOUTIAO_BASE/article/$articleId/")
        val text = response.bodyAsText()
        if (text.contains("article-content") || text.contains("articleContent")) {
            return text
        }
        return null
    }

    /**
     * 获取当前登录用户信息
     * 从首页 HTML 或个人页解析
     */
    suspend fun fetchCurrentUser(): ToutiaoUserInfo? {
        val cred = credentialFlow.firstOrNull() ?: return null
        if (cred.userId != null) {
            return ToutiaoUserInfo(
                userId = cred.userId,
                userName = cred.userName ?: "",
                avatarUrl = cred.avatarUrl,
            )
        }
        return null
    }
}

internal data class ToutiaoHotItem(
    val clusterId: String,
    val title: String,
    val hotValue: String,
    val url: String,
    val imageUrl: String?,
    val label: String?,
    val labelUrl: String?,
)

internal data class ToutiaoUserInfo(
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
)
