package dev.dimension.flare.data.network.wordpress

import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * WordPress REST API 客户端
 * 通用设计，支持任意 WordPress 站点
 */
internal class WordPressApi(
    /** 站点基础 URL，如 https://www.gamer520.com */
    private val baseUrl: String,
) {
    /** 强制使用 HTTPS（Android 9+ 默认禁止明文通信） */
    private val normalizedBaseUrl: String
        get() = "https://${baseUrl.removePrefix("http://").removePrefix("https://")}"

    private val apiBase: String
        get() = "$normalizedBaseUrl/wp-json/wp/v2"

    private val jsonApiBase: String
        get() = "$normalizedBaseUrl/wp-json"

    private fun httpClient(): HttpClient = ktorClient {
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
            }
        }
    }

    /**
     * 探测站点是否为 WordPress，返回站点信息
     */
    suspend fun detectSite(): WPSiteInfo? {
        return try {
            val text = httpClient().get("$jsonApiBase/").bodyAsText()
            json.decodeFromString<WPSiteInfo>(text)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取文章列表
     * @param page 页码，从 1 开始
     * @param perPage 每页数量
     * @param categoryId 按分类筛选
     * @param search 搜索关键词
     */
    suspend fun fetchPosts(
        page: Int = 1,
        perPage: Int = 20,
        categoryId: Long? = null,
        search: String? = null,
    ): List<WPPost> {
        val text = httpClient().get("$apiBase/posts") {
            parameter("page", page)
            parameter("per_page", perPage)
            parameter("_embed", "wp:featuredmedia,author,wp:term")
            categoryId?.let { parameter("categories", it) }
            search?.let { parameter("search", it) }
        }.bodyAsText()
        return try {
            json.decodeFromString<List<WPPost>>(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 获取分类列表
     */
    suspend fun fetchCategories(perPage: Int = 100): List<WPCategory> {
        val text = try {
            httpClient().get("$apiBase/categories") {
                parameter("per_page", perPage)
                parameter("hide_empty", "true")
            }.bodyAsText()
        } catch (_: Exception) {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<WPCategory>>(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 获取文章总数（从响应头 X-WP-Total 获取）
     */
    suspend fun fetchPostCount(): Int {
        return try {
            val response = httpClient().get("$apiBase/posts") {
                parameter("per_page", 1)
            }
            response.headers["X-WP-Total"]?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
