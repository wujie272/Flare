package dev.dimension.flare.data.network.coolapk

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.CoolapkCredential
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

public class CoolapkService(
    private val credentialFlow: Flow<CoolapkCredential>,
    private val onCredentialRefreshed: (CoolapkCredential) -> Unit = {},
) {
    private var cachedCredential: CoolapkCredential? = null

    public suspend fun currentCredential(): CoolapkCredential? {
        if (cachedCredential == null) {
            cachedCredential = credentialFlow.firstOrNull()
        }
        return cachedCredential
    }

    private fun refreshCredential() {
        cachedCredential = null
    }

    private suspend fun buildAuthHeaders(): Map<String, String> {
        val cred = currentCredential() ?: return emptyMap()
        val deviceCode = cred.deviceCode.ifEmpty { CoolapkAuthUtil.generateDeviceCode() }
        val appToken = CoolapkAuthUtil.generateAppToken(deviceCode)
        val headers = CoolapkAuthUtil.buildHeaders(deviceCode, appToken).toMutableMap()
        if (cred.rawCookie.isNotBlank()) headers["Cookie"] = cred.rawCookie
        return headers
    }


    // ==================== 首页 Feed ====================

    /** 获取首页 Feed 列表 */
    public suspend fun fetchMainIndex(page: Int = 1): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/main/index") {
                    parameter("page", page)
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            println("[CoolapkService] fetchMainIndex error: ${e.message}")
            emptyList()
        }

    // ==================== 用户 ====================

    /** 获取用户信息 */
    public suspend fun fetchUserProfile(uid: String): JsonObject? =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/user/profile") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("uid", uid)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonObject
        } catch (e: Exception) {
            null
        }

    /** 获取关注列表 */
    public suspend fun fetchFollowList(
        uid: String,
        page: Int = 1,
    ): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/user/followList") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("uid", uid)
                    parameter("page", page)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    /** 获取粉丝列表 */
    public suspend fun fetchFansList(
        uid: String,
        page: Int = 1,
    ): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/user/fansList") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("uid", uid)
                    parameter("page", page)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    // ==================== 动态 ====================

    /** 获取动态详情 */
    public suspend fun fetchFeedDetail(id: String): JsonObject? =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/feed/detail") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("id", id)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonObject
        } catch (e: Exception) {
            null
        }

    /** 获取关注列表动态（分页） */
    public suspend fun fetchFollowFeed(
        page: Int = 1,
        firstItem: String? = null,
        lastItem: String? = null,
    ): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/page/dataList") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("url", "V9_HOME_TAB_FOLLOW")
                    parameter("type", "circle")
                    parameter("page", page)
                    firstItem?.let { parameter("firstItem", it) }
                    lastItem?.let { parameter("lastItem", it) }
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            println("[CoolapkService] fetchFollowFeed error: ${e.message}")
            emptyList()
        }

    /** 获取用户动态列表 */
    public suspend fun fetchUserFeed(
        uid: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/user/feedList") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("uid", uid)
                    parameter("page", page)
                    parameter("pageSize", pageSize)
                    parameter("isIncludeTop", 1)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            println("[CoolapkService] fetchUserFeed error: ${e.message}")
            emptyList()
        }

    /** 获取酷图列表 */
    public suspend fun fetchCoolPic(page: Int = 1): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/page/dataList") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("url", "V11_FIND_COOLPIC")
                    parameter("page", page)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            println("[CoolapkService] fetchCoolPic error: ${e.message}")
            emptyList()
        }

    // ==================== 通知 ====================

    /** 获取通知列表 */
    public suspend fun fetchNotificationList(
        type: String = "atme",
        page: Int = 1,
    ): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/notification/list") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("type", type)
                    parameter("page", page)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    // ==================== 搜索 ====================

    /** 搜索 */
    public suspend fun fetchSearch(
        type: String = "feed",
        query: String,
        page: Int = 1,
    ): List<JsonObject> =
        try {
            val response =
                httpClient().get("https://api.coolapk.com/v6/search") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("type", type)
                    parameter("q", query)
                    parameter("page", page)
                }
            val body = response.body<JsonObject>()
            body["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    // ==================== 点赞 ====================

    /** 点赞 */
    public suspend fun like(feedId: String): Boolean =
        try {
            val response =
                httpClient().post("https://api.coolapk.com/v6/feed/like") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("id", feedId)
                }
            val body = response.body<JsonObject>()
            body["status"]?.jsonPrimitive?.content == "1"
        } catch (e: Exception) {
            println("[CoolapkService] like error: ${e.message}")
            false
        }

    /** 取消点赞 */
    public suspend fun unlike(feedId: String): Boolean =
        try {
            val response =
                httpClient().post("https://api.coolapk.com/v6/feed/unlike") {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                    parameter("id", feedId)
                }
            val body = response.body<JsonObject>()
            body["status"]?.jsonPrimitive?.content == "1"
        } catch (e: Exception) {
            println("[CoolapkService] unlike error: ${e.message}")
            false
        }

    private fun httpClient(): HttpClient = ktorClient { }
}
