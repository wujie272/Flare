package dev.dimension.flare.data.network.bilibili

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.bilibili.BilibiliCredential
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import io.ktor.client.request.forms.submitForm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

private const val API_BASE = "https://api.bilibili.com"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Bilibili API 客户端
 */
internal class BilibiliService(
    private val credentialFlow: Flow<BilibiliCredential>,
    private val onCredentialRefreshed: (BilibiliCredential) -> Unit = {},
) {
    private var cachedCredential: BilibiliCredential? = null
    private var cachedWbiKeys: Pair<String, String>? = null
    private var wbiKeyFetchTime: Long = 0

    suspend fun currentCredential(): BilibiliCredential? {
        if (cachedCredential == null) {
            cachedCredential = credentialFlow.firstOrNull()
        }
        return cachedCredential
    }

    /** 创建 HTTP 客户端（带认证 Cookie） */
    private suspend fun httpClient(): HttpClient {
        currentCredential()
        return ktorClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            defaultRequest {
                header("User-Agent", USER_AGENT)
                header("Origin", "https://www.bilibili.com")
                val cred = cachedCredential
            if (cred != null) {
                val cookies = buildString {
                    cred.sessdata?.let { append("SESSDATA=$it; ") }
                    cred.biliJct?.let { append("bili_jct=$it; ") }
                    cred.buvid3?.let { append("buvid3=$it; ") }
                    cred.buvid4?.let { append("buvid4=$it; ") }
                    cred.dedeUserId?.let { append("DedeUserID=$it; ") }
                    cred.dedeUserIdCkMd5?.let { append("DedeUserID__ckMd5=$it; ") }
                }.trimEnd(' ').trimEnd(';')
                if (cookies.isNotEmpty()) {
                    header("Cookie", cookies)
                }
            }
        }
    }
    }

    // ==================== WBI Key 管理 ====================

    private suspend fun getWbiKeys(): Pair<String, String> {
        val now = Clock.System.now().toEpochMilliseconds()
        if (cachedWbiKeys != null && (now - wbiKeyFetchTime) < 30 * 60 * 1000) {
            return cachedWbiKeys!!
        }
        val response = httpClient().get("$API_BASE/x/web-interface/nav") {
            header("Referer", "https://www.bilibili.com")
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        val data = obj["data"]?.jsonObject
        val wbiImg = data?.get("wbi_img")?.jsonObject ?: data?.get("wbi_img_")?.jsonObject
        if (wbiImg != null) {
            val imgUrl = wbiImg["img_url"]?.jsonPrimitive?.content ?: ""
            val subUrl = wbiImg["sub_url"]?.jsonPrimitive?.content ?: ""
            val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
            val subKey = subUrl.substringAfterLast("/").substringBefore(".")
            if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
                cachedWbiKeys = imgKey to subKey
                wbiKeyFetchTime = now
                return cachedWbiKeys!!
            }
        }
        throw IllegalStateException("Failed to get WBI keys")
    }

    // ==================== 公共 API ====================

    suspend fun getNavInfo(): JsonObject? {
        val response = httpClient().get("$API_BASE/x/web-interface/nav") {
            header("Referer", "https://www.bilibili.com")
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    suspend fun getRecommendedFeed(idx: Int = 0, refreshCount: Int = 30): List<JsonObject> {
        val (imgKey, subKey) = getWbiKeys()
        val params = signWbi(
            params = mapOf(
                "ps" to refreshCount.toString(),
                "fresh_type" to "3",
                "fresh_idx" to idx.toString(),
                "feed_version" to Clock.System.now().toEpochMilliseconds().toString(),
                "y_num" to idx.toString(),
            ),
            imgKey = imgKey,
            subKey = subKey,
        )
        val response = httpClient().get("$API_BASE/x/web-interface/wbi/index/top/feed/rcmd") {
            params.forEach { (key, value) -> parameter(key, value) }
            // WBI 接口不能带 Referer
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["data"]?.jsonObject?.get("item")?.jsonArray?.map { it.jsonObject } ?: emptyList()
    }

    suspend fun getPopularVideos(page: Int = 1, pageSize: Int = 30): List<JsonObject> {
        val response = httpClient().get("$API_BASE/x/web-interface/popular") {
            header("Referer", "https://www.bilibili.com")
            parameter("pn", page)
            parameter("ps", pageSize)
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["data"]?.jsonObject?.get("list")?.jsonArray?.map { it.jsonObject } ?: emptyList()
    }

    suspend fun getVideoInfo(bvid: String): JsonObject? {
        val response = httpClient().get("$API_BASE/x/web-interface/view") {
            header("Referer", "https://www.bilibili.com/video/$bvid")
            parameter("bvid", bvid)
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    suspend fun getSpaceInfo(mid: Long): JsonObject? {
        val (imgKey, subKey) = getWbiKeys()
        val params = signWbi(params = mapOf("mid" to mid.toString()), imgKey = imgKey, subKey = subKey)
        val response = httpClient().get("$API_BASE/x/space/wbi/acc/info") {
            header("Referer", "https://space.bilibili.com/$mid")
            params.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    suspend fun getSpaceVideos(mid: Long, page: Int = 1, pageSize: Int = 30): List<JsonObject> {
        val (imgKey, subKey) = getWbiKeys()
        val params = signWbi(
            params = mapOf("mid" to mid.toString(), "pn" to page.toString(), "ps" to pageSize.toString()),
            imgKey = imgKey,
            subKey = subKey,
        )
        val response = httpClient().get("$API_BASE/x/space/wbi/arc/search") {
            header("Referer", "https://space.bilibili.com/$mid")
            params.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["data"]?.jsonObject?.get("list")?.jsonObject?.get("vlist")?.jsonArray?.map { it.jsonObject }
            ?: obj["data"]?.jsonObject?.get("archives")?.jsonArray?.map { it.jsonObject }
            ?: emptyList()
    }

    suspend fun getDynamicFeed(offset: String = "", type: String = "all"): JsonObject? {
        val response = httpClient().get("$API_BASE/x/polymer/web-dynamic/v1/feed/all") {
            header("Referer", "https://t.bilibili.com")
            header("Origin", "https://t.bilibili.com")
            parameter("type", type)
            parameter("offset", offset)
            parameter("platform", "web")
            parameter("features", "itemOpusStyle,listOnlyfans")
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    suspend fun searchAll(keyword: String, page: Int = 1, pageSize: Int = 30): JsonObject? {
        val (imgKey, subKey) = getWbiKeys()
        val params = signWbi(
            params = mapOf("keyword" to keyword, "page" to page.toString(), "page_size" to pageSize.toString()),
            imgKey = imgKey,
            subKey = subKey,
        )
        val response = httpClient().get("$API_BASE/x/web-interface/wbi/search/all/v2") {
            header("Referer", "https://search.bilibili.com")
            header("Origin", "https://search.bilibili.com")
            params.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    /** 获取排行榜视频 */
    suspend fun getRankingVideos(rid: Int = 0, type: String = "all"): List<JsonObject> {
        val response = httpClient().get("$API_BASE/x/web-interface/ranking/v2") {
            header("Referer", "https://www.bilibili.com")
            parameter("rid", rid)
            parameter("type", type)
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["data"]?.jsonObject?.get("list")?.jsonArray?.map { it.jsonObject } ?: emptyList()
    }

    // ==================== 视频播放 ====================

    /** 获取视频播放地址（旧版 API，不需要 Referer 头） */
    suspend fun getPlayUrlLegacy(bvid: String, cid: Long, qn: Int = 64): String? {
        val response = httpClient().get("$API_BASE/x/player/playurl") {
            header("Referer", "https://www.bilibili.com/video/$bvid")
            parameter("bvid", bvid)
            parameter("cid", cid)
            parameter("qn", qn)
            parameter("fnval", 16)
            parameter("fnver", 0)
            parameter("fourk", 1)
            parameter("platform", "html5")
            parameter("high_quality", 1)
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content != "0") return null
        val data = obj["data"]?.jsonObject ?: return null
        val durl = data["durl"]?.jsonArray
        if (durl != null && durl.isNotEmpty()) {
            return durl[0].jsonObject["url"]?.jsonPrimitive?.content
        }
        return null
    }

    /** 获取视频播放地址（从 playurl API 获取 durl 中的第一个视频地址） */
    suspend fun getPlayUrl(bvid: String, cid: Long, qn: Int = 80): String? {
        val (imgKey, subKey) = getWbiKeys()
        val params = signWbi(
            params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "platform" to "web",
                "high_quality" to "1",
            ),
            imgKey = imgKey,
            subKey = subKey,
        )
        val response = httpClient().get("$API_BASE/x/player/wbi/playurl") {
            header("Referer", "https://www.bilibili.com/video/$bvid")
            params.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content != "0") return null
        val data = obj["data"]?.jsonObject ?: return null
        // 优先用 durl（mp4 直链）
        val durl = data["durl"]?.jsonArray
        if (durl != null && durl.isNotEmpty()) {
            return durl[0].jsonObject["url"]?.jsonPrimitive?.content
        }
        // 兜底：dash 第一个视频流
        val dash = data["dash"]?.jsonObject
        val video = dash?.get("video")?.jsonArray
        if (video != null && video.isNotEmpty()) {
            return video[0].jsonObject["base_url"]?.jsonPrimitive?.content
        }
        return null
    }

    // ==================== 通知 ====================

    /** 获取未读消息数 */
    suspend fun getFeedUnread(): JsonObject? {
        val response = httpClient().get("$API_BASE/x/msgfeed/unread") {
            header("Referer", "https://www.bilibili.com")
            parameter("build", "0")
            parameter("mobi_app", "web")
        }.bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    // ==================== 评论 ====================

    /** 获取视频评论列表 */
    suspend fun getComments(oid: Long, page: Int = 1, pageSize: Int = 20): JsonObject? {
        val (imgKey, subKey) = getWbiKeys()
        val params = signWbi(
            params = mapOf(
                "oid" to oid.toString(),
                "type" to "1",
                "mode" to "3",
                "ps" to pageSize.toString(),
                "pn" to page.toString(),
            ),
            imgKey = imgKey,
            subKey = subKey,
        )
        val response = httpClient().get("$API_BASE/x/v2/reply/wbi/main") {
            header("Referer", "https://www.bilibili.com")
            params.forEach { (key, value) -> parameter(key, value) }
        }.bodyAsText()
        if (!response.startsWith("{")) return null
        val obj = try {
            json.parseToJsonElement(response).jsonObject
        } catch (_: Exception) {
            return null
        }
        if (obj["code"]?.jsonPrimitive?.content == "0") return obj["data"]?.jsonObject
        return null
    }

    // ==================== 互动 API ====================

    suspend fun likeVideo(aid: Long, like: Boolean): Boolean {
        val cred = currentCredential() ?: return false
        return httpClient().submitForm(
            url = "$API_BASE/x/web-interface/archive/like",
            formParameters = Parameters.build {
                append("aid", aid.toString())
                append("like", if (like) "1" else "2")
                append("csrf", cred.biliJct.orEmpty())
            }
        ) {
            header("Referer", "https://www.bilibili.com")
        }.let { response ->
            val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
            obj["code"]?.jsonPrimitive?.content == "0"
        }
    }

    suspend fun modifyRelation(fid: Long, act: Int): Boolean {
        val cred = currentCredential() ?: return false
        return httpClient().submitForm(
            url = "$API_BASE/x/relation/modify",
            formParameters = Parameters.build {
                append("fid", fid.toString())
                append("act", act.toString())
                append("csrf", cred.biliJct.orEmpty())
                append("re_src", "11")
            }
        ) {
            header("Referer", "https://space.bilibili.com")
        }.let { response ->
            val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
            obj["code"]?.jsonPrimitive?.content == "0"
        }
    }
}
