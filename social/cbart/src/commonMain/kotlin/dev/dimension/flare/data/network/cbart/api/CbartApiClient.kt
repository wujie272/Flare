package dev.dimension.flare.data.network.cbart.api

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.CbartCredential
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

/**
 * 妖狐吧 API 客户端
 * 仅使用 linzijun.app（Laravel 新站），shenmatk.com（旧 PHP 站）已废弃
 * 认证：Session + CSRF token，三步握手获取 session
 * 419 自动重试，23h TTL，12h 心跳保活
 */
private const val LINZIJUN_HOST = "https://linzijun.app"

internal class CbartApiClient(
    private val credentialFlow: Flow<CbartCredential>,
    private val accountKey: MicroBlogKey? = null,
    private val onCredentialUpdated: suspend (CbartCredential) -> Unit = {},
) {
    private var cachedCredential: CbartCredential? = null

    private val SESSION_TTL_MS: Long = 23 * 60 * 60 * 1000L
    private val HEARTBEAT_INTERVAL_MS: Long = 12 * 60 * 60 * 1000L

    private suspend fun credential(): CbartCredential? {
        if (cachedCredential == null) {
            cachedCredential = credentialFlow.firstOrNull()
        }
        return cachedCredential
    }

    private fun httpClient(): HttpClient = ktorClient {}

    /**
     * 刷新/获取 Laravel session
     * - 如果已有 session，直接 GET / 绑定 CSRF token（保留认证状态）
     * - 如果没有 session，三步握手获取新 session
     * - 如果 credential 有 email+password，尝试登录
     */
    suspend fun refreshLaravelSession(): CbartCredential? {
        val cred = credential() ?: return null
        val ua = "Mozilla/5.0 (Linux; Android 16; Redmi K80 Pro)"

        // 已有 session → 只做 GET / 绑定 CSRF，保留认证状态
        if (cred.laravelSession != null) {
            return bindCsrfToExistingSession(cred, ua)
        }

        // 无 session → 三步握手获取新 session
        val response1 = httpClient().get(LINZIJUN_HOST) {
            headers { append("User-Agent", ua) }
        }
        val html1 = response1.bodyAsText()
        val csrfToken1 = Regex("""<meta name="csrf-token" content="([^"]+)""")
            .find(html1)?.groupValues?.getOrNull(1) ?: return cred
        val cookie1 = response1.headers.getAll("Set-Cookie")
            ?.joinToString("; ") { it.substringBefore(";") } ?: ""

        val response2 = httpClient().post("$LINZIJUN_HOST/api/video_list") {
            headers {
                append("Cookie", cookie1)
                append("X-CSRF-TOKEN", csrfToken1)
                append("X-Requested-With", "XMLHttpRequest")
                append("User-Agent", ua)
            }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("limit=1&page=1&order=posttime")
        }
        val allCookies2 = response2.headers.getAll("Set-Cookie")?.joinToString("; ") ?: ""
        val laravelSession = Regex("laravel_session=([^;]+)")
            .find(allCookies2)?.groupValues?.getOrNull(1) ?: return cred
        val xsrfToken = Regex("XSRF-TOKEN=([^;]+)")
            .find(allCookies2)?.groupValues?.getOrNull(1)

        val response3 = httpClient().get(LINZIJUN_HOST) {
            headers {
                append("Cookie", "laravel_session=$laravelSession; $cookie1")
                append("User-Agent", ua)
            }
        }
        val html3 = response3.bodyAsText()
        val csrfToken3 = Regex("""<meta name="csrf-token" content="([^"]+)""")
            .find(html3)?.groupValues?.getOrNull(1)

        val sessionCookie = "laravel_session=$laravelSession; $cookie1"
        var loginResult: CbartCredential? = null
        val email = cred.email ?: cred.userName
        if (email != null && cred.password != null) {
            loginResult = try {
                loginLaravelSession(sessionCookie, csrfToken3 ?: csrfToken1, email, cred.password, ua)
            } catch (_: Exception) { null }
        }

        return (loginResult ?: cred.copy(laravelSessionLoggedIn = false)).copy(
            laravelSession = laravelSession,
            xsrfToken = xsrfToken ?: cred.xsrfToken,
            csrfToken = csrfToken3 ?: cred.csrfToken,
            sessionCreatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    /**
     * 已有 session 时，只做 GET / 绑定 CSRF token，不创建新 session
     */
    private suspend fun bindCsrfToExistingSession(cred: CbartCredential, ua: String): CbartCredential? {
        val cookie = buildString {
            append("laravel_session=${cred.laravelSession}")
            if (cred.xsrfToken != null) append("; XSRF-TOKEN=${cred.xsrfToken}")
        }
        return try {
            val html = httpClient().get(LINZIJUN_HOST) {
                headers {
                    append("Cookie", cookie)
                    append("User-Agent", ua)
                }
            }.bodyAsText()
            val csrf = Regex("""<meta name="csrf-token" content="([^"]+)""")
                .find(html)?.groupValues?.getOrNull(1)
            cred.copy(
                csrfToken = csrf ?: cred.csrfToken,
                sessionCreatedAt = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (_: Exception) { null }
    }

    private suspend fun loginLaravelSession(
        sessionCookie: String, csrfToken: String, email: String, password: String, ua: String,
    ): CbartCredential? {
        val cred = credential() ?: return null
        val loginPage = httpClient().get("$LINZIJUN_HOST/login") {
            headers { append("Cookie", sessionCookie); append("User-Agent", ua) }
        }
        val loginCsrf = Regex("""<meta name="csrf-token" content="([^"]+)""")
            .find(loginPage.bodyAsText())?.groupValues?.getOrNull(1) ?: return cred

        val loginResponse = httpClient().post("$LINZIJUN_HOST/login") {
            headers {
                append("Cookie", sessionCookie)
                append("Content-Type", "application/x-www-form-urlencoded")
                append("User-Agent", ua)
                append("Referer", "$LINZIJUN_HOST/login")
                append("Origin", LINZIJUN_HOST)
            }
            setBody("_token=$loginCsrf&username=$email&password=$password")
        }
        if (loginResponse.status.value == 419) return null

        val finalPage = httpClient().get(LINZIJUN_HOST) {
            headers { append("Cookie", sessionCookie); append("User-Agent", ua) }
        }
        val finalCsrf = Regex("""<meta name="csrf-token" content="([^"]+)""")
            .find(finalPage.bodyAsText())?.groupValues?.getOrNull(1)

        // 登录成功后获取用户信息
        val userInfo = try {
            val ua = "Mozilla/5.0 (Linux; Android 16; Redmi K80 Pro)"
            val text = httpClient().get("$LINZIJUN_HOST/api/user") {
                headers {
                    append("Cookie", sessionCookie)
                    append("X-CSRF-TOKEN", finalCsrf ?: csrfToken)
                    append("X-Requested-With", "XMLHttpRequest")
                    append("Accept", "application/json")
                    append("User-Agent", ua)
                }
            }.bodyAsText()
            if (!text.contains("Unauthenticated")) {
                apiJson.decodeFromString<CbartUserResponse>(text)
            } else null
        } catch (_: Exception) { null }

        return cred.copy(
            csrfToken = finalCsrf ?: cred.csrfToken,
            sessionCreatedAt = Clock.System.now().toEpochMilliseconds(),
            laravelSessionLoggedIn = true,
            userId = userInfo?.id?.toString() ?: cred.userId,
            userName = userInfo?.name ?: cred.userName,
            nickName = userInfo?.nickName ?: cred.nickName,
            avatarUrl = userInfo?.avatarUrl ?: cred.avatarUrl,
            purchasedVideoIds = userInfo?.purchasedVideoIds ?: cred.purchasedVideoIds,
        )
    }

    private fun linzijunHeaders(cred: CbartCredential?): Map<String, String> = mapOf(
        "Cookie" to "laravel_session=${cred?.laravelSession ?: ""}; XSRF-TOKEN=${cred?.xsrfToken ?: ""}",
        "X-CSRF-TOKEN" to (cred?.csrfToken ?: ""),
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Accept-Language" to "zh-CN,en;q=0.9",
    )

    private suspend fun heartbeatSession(): CbartCredential? {
        val cred = credential() ?: return null
        if (cred.laravelSession == null) return null
        return try {
            val html = httpClient().get(LINZIJUN_HOST) {
                headers {
                    append("Cookie", "laravel_session=${cred.laravelSession}; XSRF-TOKEN=${cred.xsrfToken ?: ""}")
                    append("User-Agent", "Mozilla/5.0 (Linux; Android 16; Redmi K80 Pro)")
                }
            }.bodyAsText()
            val csrf = Regex("""<meta name="csrf-token" content="([^"]+)""").find(html)?.groupValues?.getOrNull(1)
            cred.copy(csrfToken = csrf ?: cred.csrfToken, sessionCreatedAt = Clock.System.now().toEpochMilliseconds())
        } catch (_: Exception) { null }
    }

    private suspend fun ensureSession(): CbartCredential? {
        val cred = credential() ?: return null
        val elapsed = Clock.System.now().toEpochMilliseconds() - (cred.sessionCreatedAt ?: 0L)

        if (cred.laravelSession != null && elapsed < SESSION_TTL_MS) {
            if (elapsed > HEARTBEAT_INTERVAL_MS) {
                heartbeatSession()?.let { cachedCredential = it; onCredentialUpdated(it); return it }
            }
            return cred
        }

        val refreshed = refreshLaravelSession()
        if (refreshed?.laravelSession != null) {
            val withTs = refreshed.copy(sessionCreatedAt = Clock.System.now().toEpochMilliseconds())
            cachedCredential = withTs; onCredentialUpdated(withTs)
            if (cred.laravelSessionLoggedIn && !refreshed.laravelSessionLoggedIn) {
                throw LoginExpiredException(accountKey ?: MicroBlogKey("", ""), PlatformType.Cbart)
            }
            return withTs
        }
        if (cred.laravelSession != null) return cred
        return null
    }

    private suspend inline fun <reified T> linzijunApi(path: String, params: Map<String, String> = emptyMap()): T? {
        val headerMap = linzijunHeaders(ensureSession())
        return try {
            val text = httpClient().post("$LINZIJUN_HOST/api/$path") {
                headers { headerMap.forEach { (k, v) -> append(k, v) } }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(params.map { (k, v) -> "$k=$v" }.joinToString("&"))
            }.bodyAsText()
            if (text.contains("CSRF token mismatch")) {
                refreshLaravelSession()?.let { cachedCredential = it; onCredentialUpdated(it) }
                return linzijunApiRetry(path, params)
            }
            tryParse(text)
        } catch (_: Exception) { null }
    }

    private suspend inline fun <reified T> linzijunApiRetry(path: String, params: Map<String, String> = emptyMap()): T? {
        val headerMap = linzijunHeaders(credential())
        return try {
            tryParse(httpClient().post("$LINZIJUN_HOST/api/$path") {
                headers { headerMap.forEach { (k, v) -> append(k, v) } }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(params.map { (k, v) -> "$k=$v" }.joinToString("&"))
            }.bodyAsText())
        } catch (_: Exception) { null }
    }

    private inline fun <reified T> tryParse(text: String): T? =
        try { apiJson.decodeFromString<T>(text) } catch (_: Exception) { null }

    // ==================== API 方法 ====================

    suspend fun articleList(page: Int = 1, limit: Int = 20): LzjArticleListResponse? {
        val response: LinzijunListResponse<LzjArticleItem>? = linzijunApi("article_list", mapOf("page" to page.toString(), "limit" to limit.toString(), "news" to "1"))
        return response?.let { LzjArticleListResponse(success = if (it.code == 200) 1 else 0, list = it.data?.contents ?: emptyList()) }
    }

    suspend fun videoList(page: Int = 1, limit: Int = 20, order: String? = "posttime", area: String? = null, rank: String? = null, filterOneday: Boolean = false, fav: Boolean = false, purchased: Boolean = false, keyword: String? = null): LzjVideoListResponse? {
        val params = mutableMapOf("limit" to limit.toString(), "page" to page.toString(), "order" to (order ?: "posttime"), "get_owner" to "1")
        area?.let { params["area"] = it }; rank?.let { params["rank"] = it }
        if (filterOneday) params["oneday"] = "1"; if (fav) params["fav"] = "1"; if (purchased) params["purchased_video"] = "1"
        keyword?.let { params["keyword"] = it }
        return linzijunApi("video_list", params)
    }

    suspend fun videoDetail(videoId: String): LzjVideoDetailResponse? = linzijunApi("video_detail", mapOf("id" to videoId))

    /**
     * 收藏/取消收藏
     * POST /api/update_video_fav
     * 返回 {code: 200, data: {update: "+"}} 或 {update: "-"}
     */
    suspend fun toggleVideoFav(videoId: String): Boolean {
        val headerMap = linzijunHeaders(ensureSession())
        return try {
            val text = httpClient().post("$LINZIJUN_HOST/api/update_video_fav") {
                headers { headerMap.forEach { (k, v) -> append(k, v) } }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("video_id=$videoId")
            }.bodyAsText()
            text.contains("\"update\":\"+\"") || text.contains("\"update\":\"-\"")
        } catch (_: Exception) { false }
    }

    /**
     * 获取当前用户信息
     * GET /api/user
     * 需要已登录的 session，未登录返回 401 Unauthenticated
     */
    suspend fun fetchUserInfo(): CbartUserResponse? {
        val headerMap = linzijunHeaders(ensureSession())
        return try {
            val text = httpClient().get("$LINZIJUN_HOST/api/user") {
                headers { headerMap.forEach { (k, v) -> append(k, v) } }
            }.bodyAsText()
            if (text.contains("Unauthenticated") || text.contains("message")) return null
            apiJson.decodeFromString<CbartUserResponse>(text)
        } catch (_: Exception) { null }
    }
}
