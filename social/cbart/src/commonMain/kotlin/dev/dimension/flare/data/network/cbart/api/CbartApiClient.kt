package dev.dimension.flare.data.network.cbart.api

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.CbartCredential
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val CBART_API_BASE = "https://www.cbart.net/api"
private const val CBART_BASE = "https://www.cbart.net"
private const val CBART_BASE_NO_WWW = "https://cbart.net"
private const val CBART_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Cbart 真实 API 客户端。
 * 所有请求都走 `https://www.cbart.net/api/` 路径（带 www）。
 * 需要携带登录 Cookie。
 */
internal class CbartApiClient(
    private val credentialFlow: Flow<CbartCredential>,
) {
    private suspend fun sessionId(): String? =
        credentialFlow.firstOrNull()?.sessionId

    private suspend fun buildCookie(): String? =
        sessionId()?.let { "PHPSESSID=$it; cookie_enabled=1" }

    private suspend fun httpClient(): HttpClient {
        val cookie = buildCookie()
        return ktorClient {
            defaultRequest {
                headers {
                    append("User-Agent", CBART_USER_AGENT)
                    append("Accept", "application/json, text/javascript, */*; q=0.01")
                    append("Accept-Language", "zh-CN,en;q=0.9")
                    append("X-Requested-With", "XMLHttpRequest")
                    append("Origin", "https://cbart.net")
                    append("Referer", "https://cbart.net/")
                    cookie?.let { append("Cookie", it) }
                }
            }
        }
    }


    // ==================== 首页 HTML 刮削 ====================

    /**
     * 获取首页 HTML（纯 PHP SSR，无 JSON API）
     * 统一用 www.cbart.net 域名避免 Cookie 跨域问题
     */
    suspend fun fetchHomeHtml(): String {
        val response = httpClient().get("$CBART_BASE/home.php") {
            parameter("group_list", "1")
        }
        return response.bodyAsText()
    }

    /**
     * 获取首页 HTML（首页热门/推荐内容）
     */
    suspend fun fetchIndexHtml(): String {
        val response = httpClient().get("$CBART_BASE/index.php")
        return response.bodyAsText()
    }

    /**
     * 获取用户信息 HTML（用于解析当前登录用户信息）
     * 从 /api/get_setting.php 可获取 uid 和其他信息
     */
    suspend fun fetchUserSetting(): CbartSettingResponse? {
        return getInviteCode()
    }

    /**
     * 获取收藏列表页面
     */
    suspend fun fetchFavoritesHtml(): String {
        val response = httpClient().get("$CBART_BASE/home.php") {
            parameter("my_favorite", "1")
        }
        return response.bodyAsText()
    }

    // ==================== 统计排行 ====================

    /**
     * 获取原创作者收益排行
     * GET /api/user_original_stats.php?act=get_stats&period=week/month/all&order=earned&limit=9
     * ✅ 已确认可调用，CORS 全开
     */
    suspend fun getStats(
        period: String = "week",
        order: String = "earned",
        limit: Int = 9,
    ): CbartStatsResponse? {
        val response = httpClient().get("$CBART_API_BASE/user_original_stats.php") {
            parameter("act", "get_stats")
            parameter("period", period)
            parameter("order", order)
            parameter("limit", limit)
        }
        val text = response.bodyAsText()
        return try {
            json.decodeFromString<CbartStatsResponse>(text)
        } catch (_: Exception) {
            // 后端返回 Content-Type: text/html 但 body 是 JSON，可能解析失败
            // 尝试提取 JSON 部分
            null
        }
    }

    // ==================== 收藏 ====================

    /**
     * 收藏图片
     * GET /api/add_to_favorite.php?picId=X
     * ✅ 已确认
     */
    suspend fun addFavoritePicture(picId: String): CbartSimpleResponse? {
        val text = httpClient().get("$CBART_API_BASE/add_to_favorite.php") {
            parameter("picId", picId)
        }.bodyAsText()
        return tryParse(text)
    }

    /**
     * 取消收藏图片
     * GET /api/add_to_favorite.php?act=remove&picId=X
     * ✅ 已确认
     */
    suspend fun removeFavoritePicture(picId: String): CbartSimpleResponse? {
        val text = httpClient().get("$CBART_API_BASE/add_to_favorite.php") {
            parameter("act", "remove")
            parameter("picId", picId)
        }.bodyAsText()
        return tryParse(text)
    }

    /**
     * 收藏视频
     * GET /api/add_to_favorite.php?videoId=X
     * ✅ 已确认
     */
    suspend fun addFavoriteVideo(videoId: String): CbartSimpleResponse? {
        val text = httpClient().get("$CBART_API_BASE/add_to_favorite.php") {
            parameter("videoId", videoId)
        }.bodyAsText()
        return tryParse(text)
    }

    // ==================== 关注/取关 ====================

    /**
     * 关注/取关原创作者
     * POST ajax/user_originator_follow.php
     * 参数: type=1(关注) / type=2(取关), from_uid, to_uid
     * ✅ 已确认
     */
    suspend fun toggleFollow(
        type: Int, // 1=关注, 2=取关
        fromUid: String,
        toUid: String,
    ): CbartFollowResponse? {
        val text = httpClient().post("$CBART_BASE_NO_WWW/ajax/user_originator_follow.php") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("type=$type&from_uid=$fromUid&to_uid=$toUid")
        }.bodyAsText()
        return tryParse(text)
    }

    /**
     * 关注用户
     */
    suspend fun follow(fromUid: String, toUid: String) =
        toggleFollow(1, fromUid, toUid)

    /**
     * 取消关注用户
     */
    suspend fun unfollow(fromUid: String, toUid: String) =
        toggleFollow(2, fromUid, toUid)

    // ==================== 标签 ====================

    /**
     * 获取所有标签分类
     * GET /api/tag.php?all=1
     * ✅ 已确认
     */
    suspend fun getAllTags(): CbartTagListResponse? {
        val text = httpClient().get("$CBART_API_BASE/tag.php") {
            parameter("all", "1")
        }.bodyAsText()
        return tryParse(text)
    }

    /**
     * 根据 tag_ids 获取标签
     * POST /api/tag.php
     * ✅ 已确认
     */
    suspend fun getTagsByIds(tagIds: List<String>): CbartTagListResponse? {
        val text = httpClient().post("$CBART_API_BASE/tag.php") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("tag_ids=${tagIds.joinToString(",")}")
        }.bodyAsText()
        return tryParse(text)
    }

    // ==================== 视频 ====================

    /**
     * 获取视频播放地址
     * GET /api/video_parse.php?id=X&type=X
     * ✅ 已确认
     */
    suspend fun getVideoUrl(videoId: String, type: String = "1"): CbartVideoResponse? {
        val text = httpClient().get("$CBART_API_BASE/video_parse.php") {
            parameter("id", videoId)
            parameter("type", type)
        }.bodyAsText()
        return tryParse(text)
    }

    // ==================== 设置/用户信息 ====================

    /**
     * 获取邀请码
     * GET /api/get_setting.php?act=get_invite_code
     * ✅ 已确认
     */
    suspend fun getInviteCode(): CbartSettingResponse? {
        val text = httpClient().get("$CBART_API_BASE/get_setting.php") {
            parameter("act", "get_invite_code")
        }.bodyAsText()
        return tryParse(text)
    }

    // ==================== 工具方法 ====================

    /**
     * 尝试解析 JSON 响应。
     * 后端返回 Content-Type: text/html 但 body 是 JSON，需要手动解析。
     */
    private inline fun <reified T> tryParse(text: String): T? {
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            // 尝试解析 {success:1, msg:"..."} 格式
            try {
                val obj = json.parseToJsonElement(text).jsonObject
                if (obj["success"]?.jsonPrimitive?.content == "1" || obj["success"]?.jsonPrimitive?.content == "0") {
                    json.decodeFromJsonElement<T>(obj)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
