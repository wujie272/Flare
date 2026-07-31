package dev.dimension.flare.data.network.deviantart

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.deviantart.DeviantartCredential
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

private const val DA_BASE = "https://www.deviantart.com"
private const val DA_API = "https://www.deviantart.com/api/v1/oauth2"
private const val DA_PUPPY = "https://www.deviantart.com/_puppy"
private const val DA_UA = "Flare/1.0 (Social Client)"
private const val DA_UA_BROWSER = "Mozilla/5.0 (X11; Linux x86_64; rv:123.0) Gecko/20100101 Firefox/123.0.0"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

internal class DeviantartService(
    private val credentialFlow: Flow<DeviantartCredential>,
    private val onCredentialRefreshed: suspend (DeviantartCredential) -> Unit = {},
) {
    private var cachedCredential: DeviantartCredential? = null

    suspend fun currentCredential(): DeviantartCredential? {
        if (cachedCredential == null) {
            cachedCredential = credentialFlow.firstOrNull()
        }
        return cachedCredential
    }

    private suspend fun accessToken(): String? {
        val cred = currentCredential() ?: return null
        // 检查 token 是否过期，有过期自动刷新
        if (cred.refreshToken != null && cred.expiresIn > 0 && cred.lastRefreshEpochMillis != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            val elapsed = now - cred.lastRefreshEpochMillis
            // 提前 5 分钟刷新，避免边界情况
            if (elapsed >= (cred.expiresIn * 1000) - 300_000) {
                // 刷新后从缓存拿新 token
                refreshAccessToken()
                return currentCredential()?.accessToken
            }
        }
        return cred.accessToken
    }

    private fun httpClient() = ktorClient {
        defaultRequest {
            header("User-Agent", DA_UA)
            header("Accept", "application/json")
            header("dA-minor-version", "20210526")
        }
    }

    private suspend fun authClient(): HttpClient {
        val token = accessToken() ?: return httpClient()
        return ktorClient {
            defaultRequest {
                header("User-Agent", DA_UA)
                header("Accept", "application/json")
                header("Authorization", "Bearer $token")
                header("dA-minor-version", "20210526")
            }
        }
    }

    // ========== Auth ==========

    suspend fun verifyToken(): Boolean {
        val token = accessToken() ?: return false
        return try {
            val resp = httpClient().get("$DA_API/placebo") {
                header("Authorization", "Bearer $token")
            }
            val text = resp.bodyAsText()
            text.contains("\"status\":\"success\"")
        } catch (_: Exception) { false }
    }

    suspend fun refreshAccessToken(): DeviantartCredential? {
        val refreshToken = currentCredential()?.refreshToken ?: return null
        return try {
            // Public client (PKCE) — no client_secret needed for refresh
            val resp = httpClient().post("https://www.deviantart.com/oauth2/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("grant_type=refresh_token&client_id=73136&refresh_token=$refreshToken")
            }
            val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val newToken = obj["access_token"]?.jsonPrimitive?.content ?: return null
            val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
            val cred = currentCredential()?.copy(
                accessToken = newToken,
                refreshToken = newRefresh,
                expiresIn = expiresIn,
                lastRefreshEpochMillis = Clock.System.now().toEpochMilliseconds(),
            ) ?: return null
            cachedCredential = cred
            onCredentialRefreshed(cred)
            cred
        } catch (_: Exception) { null }
    }

    suspend fun whoami(): DeviantartUser? {
        return try {
            val resp = authClient().get("$DA_API/user/whoami")
            val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            DeviantartUser(
                userId = obj["userid"]?.jsonPrimitive?.content ?: "",
                userName = obj["username"]?.jsonPrimitive?.content ?: "",
                avatarUrl = obj["usericon"]?.jsonPrimitive?.content,
            )
        } catch (_: Exception) { null }
    }

    // ========== Browse ==========

    /**
     * Daily deviations — featured art curated by DeviantArt staff.
     * No pagination, returns a fixed set of results.
     */
    suspend fun browseDailyDeviations(): List<DeviantartDeviation> {
        return try {
            val resp = authClient().get("$DA_API/browse/dailydeviations")
            parseDeviations(resp.bodyAsText())
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Browse deviations by tag (replaces removed /browse/{newest,hot,popular,home}).
     * Supports offset-based pagination via next_offset.
     * Results are sorted by relevance/popularity, not by date.
     */
    suspend fun browseByTag(tag: String = "art", offset: Int = 0, limit: Int = 24): DeviantartPage<DeviantartDeviation> {
        return try {
            // mature_content=true is needed for tags that may return adult content
            val resp = authClient().get("$DA_API/browse/tags?tag=$tag&offset=$offset&limit=$limit&mature_content=true")
            parsePage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    suspend fun deviationDetail(deviationId: String): DeviantartDeviationDetail? {
        return try {
            val resp = authClient().get("$DA_API/deviation/$deviationId")
            val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            DeviantartDeviationDetail(
                deviationId = obj["deviationid"]?.jsonPrimitive?.content ?: "",
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content,
                artistName = obj["author"]?.jsonObject?.get("username")?.jsonPrimitive?.content ?: "",
                artistAvatar = obj["author"]?.jsonObject?.get("usericon")?.jsonPrimitive?.content,
                category = obj["category"]?.jsonPrimitive?.content,
                downloadUrl = obj["download"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                thumbnailUrl = obj["thumbs"]?.jsonArray?.lastOrNull()?.jsonObject?.get("src")?.jsonPrimitive?.content,
                previewUrl = obj["preview"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                contentUrl = obj["content"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                published = obj["published_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                isFavourite = obj["is_favourited"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                stats = obj["stats"]?.jsonObject?.let {
                    DeviantartStats(
                        favourites = it["favourites"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        comments = it["comments"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    )
                },
            )
        } catch (_: Exception) { null }
    }

    // ========== User Feed ==========

    /**
     * Get the list of users being watched by the given user (following).
     */
    suspend fun userFriends(username: String, offset: Int = 0, limit: Int = 24): DeviantartPage<DeviantartUser> {
        return try {
            val resp = authClient().get("$DA_API/user/friends/$username?offset=$offset&limit=$limit")
            parseUserPage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    /**
     * Get the list of users watching the given user (followers/fans).
     */
    suspend fun userWatchers(username: String, offset: Int = 0, limit: Int = 24): DeviantartPage<DeviantartUser> {
        return try {
            val resp = authClient().get("$DA_API/user/watchers/$username?offset=$offset&limit=$limit")
            parseUserPage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }


    /**
     * /user/statuses/ was removed by DeviantArt in 2024-07.
     * Use /user/profile/posts instead (returns journals + statuses, may be incomplete).
     */
    suspend fun userStatuses(username: String, offset: Int = 0, limit: Int = 20): DeviantartPage<DeviantartDeviation> {
        return try {
            val resp = authClient().get("$DA_API/user/profile/posts?username=$username&offset=$offset&limit=$limit")
            parsePage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    suspend fun userGallery(username: String, offset: Int = 0, limit: Int = 24): DeviantartPage<DeviantartDeviation> {
        return try {
            // NOTE: /gallery/{username} requires a folder ID, not a username.
            // Use /gallery/all?username=... instead.
            val resp = authClient().get("$DA_API/gallery/all?username=$username&offset=$offset&limit=$limit")
            parsePage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    /**
     * /user/favourites/ was removed by DeviantArt in 2024-07.
     * Use /collections/ endpoints instead.
     */
    suspend fun userFavourites(username: String, offset: Int = 0, limit: Int = 24): DeviantartPage<DeviantartDeviation> {
        return try {
            val resp = authClient().get("$DA_API/collections/all?username=$username&offset=$offset&limit=$limit")
            parsePage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    suspend fun userProfile(username: String): DeviantartUserProfile? {
        return try {
            val resp = authClient().get("$DA_API/user/profile/$username?expand=user.watch")
            val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val user = obj["user"]?.jsonObject
            DeviantartUserProfile(
                userId = user?.get("userid")?.jsonPrimitive?.content ?: "",
                userName = user?.get("username")?.jsonPrimitive?.content ?: "",
                avatarUrl = user?.get("usericon")?.jsonPrimitive?.content,
                coverUrl = obj["cover"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                tagline = obj["tagline"]?.jsonPrimitive?.content,
                artistLevel = obj["artist_level"]?.jsonPrimitive?.content,
                favouritesCount = obj["profile_favcount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                watchersCount = obj["watchers"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                friendsCount = obj["friends"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                isWatching = user?.get("watch")?.jsonObject?.get("is_watching")?.jsonPrimitive?.content?.toBoolean() ?: false,
            )
        } catch (_: Exception) { null }
    }

    // ========== Interactions ==========

    /**
     * Add deviation to favourites
     * POST /api/v1/oauth2/collections/fave
     */
    suspend fun faveDeviation(deviationId: String): Boolean {
        return try {
            val resp = authClient().post("$DA_API/collections/fave") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("deviationid=$deviationId")
            }
            resp.bodyAsText().contains("\"success\"")
        } catch (_: Exception) { false }
    }

    /**
     * Remove deviation from favourites
     * POST /api/v1/oauth2/collections/unfave
     */
    suspend fun unfaveDeviation(deviationId: String): Boolean {
        return try {
            val resp = authClient().post("$DA_API/collections/unfave") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("deviationid=$deviationId")
            }
            resp.bodyAsText().contains("\"success\"")
        } catch (_: Exception) { false }
    }

    /**
     * Watch a user (follow)
     * POST /api/v1/oauth2/user/friends/watch
     */
    suspend fun watchUser(username: String): Boolean {
        return try {
            val resp = authClient().post("$DA_API/user/friends/watch?watch=$username")
            resp.bodyAsText().contains("\"success\"")
        } catch (_: Exception) { false }
    }

    /**
     * Unwatch a user (unfollow)
     * POST /api/v1/oauth2/user/friends/unwatch
     */
    suspend fun unwatchUser(username: String): Boolean {
        return try {
            val resp = authClient().post("$DA_API/user/friends/unwatch?unwatch=$username")
            resp.bodyAsText().contains("\"success\"")
        } catch (_: Exception) { false }
    }

    /**
     * Fetch comments on a deviation
     * GET /api/v1/oauth2/comments/deviation/{deviationId}
     */
    suspend fun fetchComments(deviationId: String, offset: Int = 0, limit: Int = 20): DeviantartPage<DeviantartComment> {
        return try {
            val resp = authClient().get("$DA_API/comments/deviation/$deviationId?offset=$offset&limit=$limit")
            val text = resp.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            val results = root["results"]?.jsonArray ?: root["data"]?.jsonArray ?: return DeviantartPage(emptyList(), true)
            val hasMore = root["has_more"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextOffset = root["next_offset"]?.jsonPrimitive?.content?.toIntOrNull()
            val comments = results.mapNotNull { element ->
                val obj = element.jsonObject
                try {
                    val user = obj["user"]?.jsonObject
                    DeviantartComment(
                        commentId = obj["commentid"]?.jsonPrimitive?.content ?: "",
                        body = obj["body"]?.jsonPrimitive?.content ?: "",
                        userName = user?.get("username")?.jsonPrimitive?.content ?: "",
                        userAvatar = user?.get("usericon")?.jsonPrimitive?.content,
                        posted = obj["posted"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        replyCount = obj["reply_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                } catch (_: Exception) { null }
            }
            DeviantartPage(data = comments, isEnd = !hasMore, nextOffset = nextOffset)
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    /**
     * More Like This — fetch similar deviations
     * GET /api/v1/oauth2/browse/morelikethis/preview/{deviationId}
     */
    suspend fun moreLikeThis(deviationId: String, offset: Int = 0, limit: Int = 12): DeviantartPage<DeviantartDeviation> {
        return try {
            val resp = authClient().get("$DA_API/browse/morelikethis/preview/$deviationId?offset=$offset&limit=$limit")
            parsePage(resp.bodyAsText())
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    /**
     * Fetch notifications/messages feed
     * GET /api/v1/oauth2/messages/feed
     */
    suspend fun fetchNotifications(offset: Int = 0, limit: Int = 20): DeviantartPage<DeviantartNotification> {
        return try {
            val resp = authClient().get("$DA_API/messages/feed?offset=$offset&limit=$limit")
            val text = resp.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            val results = root["results"]?.jsonArray ?: root["data"]?.jsonArray ?: return DeviantartPage(emptyList(), true)
            val hasMore = root["has_more"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextOffset = root["next_offset"]?.jsonPrimitive?.content?.toIntOrNull()
            val notifications = results.mapNotNull { element ->
                val obj = element.jsonObject
                try {
                    DeviantartNotification(
                        id = obj["messageid"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "",
                        type = obj["type"]?.jsonPrimitive?.content ?: "",
                        subject = obj["subject"]?.jsonPrimitive?.content ?: "",
                        body = obj["body"]?.jsonPrimitive?.content ?: "",
                        ts = obj["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        fromUsername = obj["from_user"]?.jsonObject?.get("username")?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) { null }
            }
            DeviantartPage(data = notifications, isEnd = !hasMore, nextOffset = nextOffset)
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    /**
     * Post a comment on a deviation
     * POST /api/v1/oauth2/comments/post/deviation
     */
    suspend fun postComment(deviationId: String, body: String): Boolean {
        return try {
            val resp = authClient().post("$DA_API/comments/post/deviation") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("commentid=$deviationId&body=$body")
            }
            resp.bodyAsText().contains("\"success\"")
        } catch (_: Exception) { false }
    }

    // ========== Helpers ==========

    private fun parseDeviations(text: String): List<DeviantartDeviation> {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            root["results"]?.jsonArray?.mapNotNull { parseDeviation(it.jsonObject) } ?: root["data"]?.jsonArray?.mapNotNull { parseDeviation(it.jsonObject) } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun parseUserPage(text: String): DeviantartPage<DeviantartUser> {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val results = root["results"]?.jsonArray ?: return DeviantartPage(emptyList(), true)
            val hasMore = root["has_more"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextOffset = root["next_offset"]?.jsonPrimitive?.content?.toIntOrNull()
            DeviantartPage(
                data = results.mapNotNull { item ->
                    val user = item.jsonObject["user"]?.jsonObject ?: return@mapNotNull null
                    DeviantartUser(
                        userId = user["userid"]?.jsonPrimitive?.content ?: "",
                        userName = user["username"]?.jsonPrimitive?.content ?: "",
                        avatarUrl = user["usericon"]?.jsonPrimitive?.content,
                    )
                },
                isEnd = !hasMore,
                nextOffset = nextOffset,
            )
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    private fun parsePage(text: String): DeviantartPage<DeviantartDeviation> {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val results = root["results"]?.jsonArray ?: root["data"]?.jsonArray ?: return DeviantartPage(emptyList(), true)
            val hasMore = root["has_more"]?.jsonPrimitive?.content?.toBoolean() ?: root["hasMore"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextOffset = root["next_offset"]?.jsonPrimitive?.content?.toIntOrNull()
            DeviantartPage(
                data = results.mapNotNull { parseDeviation(it.jsonObject) },
                isEnd = !hasMore,
                nextOffset = nextOffset,
            )
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    private fun parseDeviation(obj: JsonObject): DeviantartDeviation? {
        return try {
            DeviantartDeviation(
                deviationId = obj["deviationid"]?.jsonPrimitive?.content ?: return null,
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content,
                artistName = obj["author"]?.jsonObject?.get("username")?.jsonPrimitive?.content
                    ?: obj["username"]?.jsonPrimitive?.content ?: "",
                artistAvatar = obj["author"]?.jsonObject?.get("usericon")?.jsonPrimitive?.content,
                category = obj["category"]?.jsonPrimitive?.content,
                downloadUrl = obj["download"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                thumbnailUrl = obj["thumbs"]?.jsonArray?.lastOrNull()?.jsonObject?.get("src")?.jsonPrimitive?.content,
                previewUrl = obj["preview"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                contentUrl = obj["content"]?.jsonObject?.get("src")?.jsonPrimitive?.content,
                published = obj["published_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                isFavourite = obj["is_favourited"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                stats = obj["stats"]?.jsonObject?.let {
                    DeviantartStats(
                        favourites = it["favourites"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        comments = it["comments"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    )
                },
                mediaType = obj["media_type"]?.jsonPrimitive?.content,
                mediaSubtype = obj["media_subtype"]?.jsonPrimitive?.content,
            )
        } catch (_: Exception) { null }
    }

    // ========== _puppy API ==========

    /**
     * Fetch session cookies and CSRF token for _puppy API.
     */
    suspend fun fetchSessionCookies(): DeviantartCredential? {
        val cred = currentCredential() ?: return null
        return try {
            val token = accessToken() ?: return null
            val client = ktorClient {}
            val resp = client.get("$DA_BASE/") {
                header("User-Agent", DA_UA_BROWSER)
                header("Accept", "text/html,application/xhtml+xml")
                header("Authorization", "Bearer $token")
            }
            val html = resp.bodyAsText()
            client.close()
            val allCookies = resp.headers.getAll("Set-Cookie")?.mapNotNull { cookie ->
                cookie.substringBefore(";").trim()
            }?.filter { it.isNotBlank() } ?: emptyList()
            val sessionCookies = allCookies.joinToString("; ")
            val match = Regex("__CSRF_TOKEN__\\s*=\\s*'([^']+)'").find(html)
            val csrfToken = match?.groupValues?.getOrNull(1)
            if (sessionCookies.isBlank() && csrfToken == null) return null
            cred.copy(
                sessionCookies = sessionCookies.ifBlank { cred.sessionCookies },
                csrfToken = csrfToken ?: cred.csrfToken,
            )
        } catch (_: Exception) { null }
    }

    private suspend fun puppyClient(): HttpClient? {
        val cred = currentCredential() ?: return null
        val cookies = cred.sessionCookies ?: return null
        return ktorClient {
            defaultRequest {
                header("User-Agent", DA_UA_BROWSER)
                header("Accept", "application/json")
                header("Cookie", cookies)
                header("dA-minor-version", "20230710")
                header("Referer", "$DA_BASE/")
            }
        }
    }

    private suspend fun puppyGet(path: String): String? {
        val client = puppyClient() ?: return null
        val cred = currentCredential() ?: return null
        val csrfToken = cred.csrfToken ?: return null
        return try {
            val url = "$DA_PUPPY/$path" +
                (if (path.contains("?")) "&" else "?") +
                "da_minor_version=20230710&csrf_token=$csrfToken"
            val resp = client.get(url)
            val text = resp.bodyAsText()
            client.close()
            if (text.contains("csrf\":\"invalid\"") || text.contains("csrf\":\"missing\"")) {
                val newCred = fetchSessionCookies()
                if (newCred != null) {
                    cachedCredential = newCred
                    onCredentialRefreshed(newCred)
                    return puppyGet(path)
                }
                return null
            }
            text
        } catch (_: Exception) { client.close(); null }
    }

    suspend fun fetchRfyFeed(page: Int = 0): DeviantartPage<DeviantartDeviation> {
        val text = puppyGet("dabrowse/networkbar/rfy/deviations?page=$page")
            ?: return DeviantartPage(emptyList(), true)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val hasMore = root["hasMore"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextCursor = root["nextCursor"]?.jsonPrimitive?.content
            val deviations = root["deviations"]?.jsonArray?.mapNotNull { parsePuppyDeviation(it.jsonObject) } ?: emptyList()
            DeviantartPage(data = deviations, isEnd = !hasMore, nextOffset = if (hasMore) (page + 1) else null)
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    suspend fun fetchSearchAll(query: String, page: Int = 0): DeviantartPage<DeviantartDeviation> {
        val text = puppyGet("dabrowse/search/all?q=${encodeUrlParam(query)}&page=$page")
            ?: return DeviantartPage(emptyList(), true)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val hasMore = root["hasMore"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextCursor = root["nextCursor"]?.jsonPrimitive?.content
            val deviations = root["deviations"]?.jsonArray?.mapNotNull { parsePuppyDeviation(it.jsonObject) } ?: emptyList()
            DeviantartPage(data = deviations, isEnd = !hasMore, nextOffset = if (hasMore) (page + 1) else null)
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    suspend fun fetchTagSearch(tag: String, page: Int = 0): DeviantartPage<DeviantartDeviation> {
        val text = puppyGet("dabrowse/networkbar/tag/deviations?tag=${encodeUrlParam(tag)}&page=$page")
            ?: return DeviantartPage(emptyList(), true)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val hasMore = root["hasMore"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val nextCursor = root["nextCursor"]?.jsonPrimitive?.content
            val deviations = root["deviations"]?.jsonArray?.mapNotNull { parsePuppyDeviation(it.jsonObject) } ?: emptyList()
            DeviantartPage(data = deviations, isEnd = !hasMore, nextOffset = if (hasMore) (page + 1) else null)
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    suspend fun fetchPuppyUserProfile(username: String): JsonObject? {
        val text = puppyGet("dauserprofile/init/about?username=$username") ?: return null
        return try { json.parseToJsonElement(text).jsonObject } catch (_: Exception) { null }
    }

    suspend fun fetchPuppyUserGallery(username: String, page: Int = 0): DeviantartPage<DeviantartDeviation> {
        val text = puppyGet("dauserprofile/init/gallery?username=$username&limit=50&page=$page&deviations_limit=50&with_subfolders=false")
            ?: return DeviantartPage(emptyList(), true)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val modules = root["gruser"]?.jsonObject?.get("page")?.jsonObject?.get("modules")?.jsonArray
                ?: return DeviantartPage(emptyList(), true)
            val folderMod = modules.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "folder_deviations" }
                ?.jsonObject?.get("moduleData")?.jsonObject?.get("folderDeviations")?.jsonObject
            val deviations = folderMod?.get("deviations")?.jsonArray?.mapNotNull { parsePuppyDeviation(it.jsonObject) } ?: emptyList()
            val hasMore = folderMod?.get("hasMore")?.jsonPrimitive?.content?.toBoolean() ?: false
            DeviantartPage(data = deviations, isEnd = !hasMore, nextOffset = if (hasMore) (page + 1) else null)
        } catch (_: Exception) { DeviantartPage(emptyList(), true) }
    }

    private fun parsePuppyDeviation(obj: JsonObject): DeviantartDeviation? {
        return try {
            val deviationId = obj["deviationId"]?.jsonPrimitive?.content ?: return null
            val author = obj["author"]?.jsonObject
            val stats = obj["stats"]?.jsonObject
            val media = obj["media"]?.jsonObject
            val mediaBaseUri = media?.get("baseUri")?.jsonPrimitive?.content
            val mediaToken = media?.get("token")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            val mediaUrl = if (mediaBaseUri != null && mediaToken != null) "$mediaBaseUri?token=$mediaToken" else null
            val types = media?.get("types")?.jsonObject
            val thumbnailUrl = types?.get("0")?.jsonObject?.get("t")?.jsonPrimitive?.content
                ?: types?.get("1")?.jsonObject?.get("t")?.jsonPrimitive?.content
            val previewUrl = types?.get("3")?.jsonObject?.get("c")?.jsonPrimitive?.content
                ?: types?.get("4")?.jsonObject?.get("c")?.jsonPrimitive?.content
            DeviantartDeviation(
                deviationId = deviationId,
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                description = null,
                artistName = author?.get("username")?.jsonPrimitive?.content ?: "",
                artistAvatar = author?.get("usericon")?.jsonPrimitive?.content,
                category = obj["type"]?.jsonPrimitive?.content,
                downloadUrl = mediaUrl,
                thumbnailUrl = thumbnailUrl,
                previewUrl = previewUrl,
                contentUrl = mediaUrl,
                published = parsePuppyDate(obj["publishedTime"]?.jsonPrimitive?.content ?: ""),
                isFavourite = obj["isFavourited"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                stats = stats?.let {
                    DeviantartStats(
                        favourites = it["favourites"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        comments = it["comments"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    )
                },
                mediaType = obj["type"]?.jsonPrimitive?.content,
                mediaSubtype = obj["filetype"]?.jsonPrimitive?.content,
            )
        } catch (_: Exception) { null }
    }

    private fun parsePuppyDate(dateStr: String): Long {
        return try {
            // _puppy API returns ISO 8601 like "2026-07-28T02:31:57+0000" (no colon in offset)
            val normalized = dateStr.replace(Regex("([+-]\\d{2})(\\d{2})$"), "\$1:\$2")
            kotlin.time.Instant.parse(normalized).toEpochMilliseconds() / 1000
        } catch (_: Exception) { 0 }
    }

    private fun encodeUrlParam(value: String): String = value.encodeURLParameter()

}

// ========== Data Models ==========

internal data class DeviantartUser(
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
)

internal data class DeviantartUserProfile(
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val tagline: String? = null,
    val artistLevel: String? = null,
    val favouritesCount: Long = 0,
    val watchersCount: Long = 0,
    val friendsCount: Long = 0,
    val isWatching: Boolean = false,
)

internal data class DeviantartDeviation(
    val deviationId: String,
    val title: String,
    val description: String? = null,
    val artistName: String,
    val artistAvatar: String? = null,
    val category: String? = null,
    val downloadUrl: String? = null,
    val thumbnailUrl: String? = null,
    val previewUrl: String? = null,
    val contentUrl: String? = null,
    val published: Long = 0,
    val isFavourite: Boolean = false,
    val stats: DeviantartStats? = null,
    val mediaType: String? = null,
    val mediaSubtype: String? = null,
)

internal data class DeviantartDeviationDetail(
    val deviationId: String,
    val title: String,
    val description: String? = null,
    val artistName: String,
    val artistAvatar: String? = null,
    val category: String? = null,
    val downloadUrl: String? = null,
    val thumbnailUrl: String? = null,
    val previewUrl: String? = null,
    val contentUrl: String? = null,
    val published: Long = 0,
    val isFavourite: Boolean = false,
    val stats: DeviantartStats? = null,
)

internal data class DeviantartStats(
    val favourites: Long = 0,
    val comments: Long = 0,
)

/**
 * 评论
 */
internal data class DeviantartComment(
    val commentId: String,
    val body: String,
    val userName: String,
    val userAvatar: String? = null,
    val posted: Long = 0,
    val replyCount: Int = 0,
)

/**
 * 通知
 */
internal data class DeviantartNotification(
    val id: String,
    val type: String,
    val subject: String,
    val body: String,
    val ts: Long = 0,
    val fromUsername: String? = null,
)

internal data class DeviantartPage<T>(
    val data: List<T>,
    val isEnd: Boolean = true,
    val nextOffset: Int? = null,
)
