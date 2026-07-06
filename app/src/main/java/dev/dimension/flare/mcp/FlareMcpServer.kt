package dev.dimension.flare.mcp

import android.content.Context
import android.util.Log
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * MCP HTTP Server for Flare.
 * Exposes Flare data as JSON REST endpoints on localhost:8899.
 * No MCP SDK dependency - plain Ktor HTTP server.
 */
class FlareMcpServer(
    private val context: Context,
    private val bridge: FlareBridge,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var server: EmbeddedServer<*, *>? = null
    private val port = 8899

    fun start() {
        try {
            server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    })
                }
                routing {
                    get("/timeline") {
                        val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 20
                        val posts = bridge.loadHomeTimeline(count.coerceIn(1, 50))
                        call.respond<ApiResponse<List<ApiPost>>>(ApiResponse(success = true, data = posts.map { it.toApiPost() }))
                    }
                    get("/search/posts") {
                        val query = call.request.queryParameters["q"] ?: return@get call.respond<ApiResponse<String>>(ApiResponse(success = false, error = "missing query parameter 'q'"))
                        val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 20
                        val posts = bridge.searchPosts(query, count.coerceIn(1, 50))
                        call.respond<ApiResponse<List<ApiPost>>>(ApiResponse(success = true, data = posts.map { it.toApiPost() }))
                    }
                    get("/search/users") {
                        val query = call.request.queryParameters["q"] ?: return@get call.respond<ApiResponse<String>>(ApiResponse(success = false, error = "missing query parameter 'q'"))
                        val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 20
                        val users = bridge.searchUsers(query, count.coerceIn(1, 50))
                        call.respond<ApiResponse<List<ApiUser>>>(ApiResponse(success = true, data = users.map { it.toApiUser() }))
                    }
                    get("/notifications") {
                        val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 20
                        val posts = bridge.getNotifications(count.coerceIn(1, 50))
                        call.respond<ApiResponse<List<ApiPost>>>(ApiResponse(success = true, data = posts.map { it.toApiPost() }))
                    }
                    get("/trending") {
                        val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 20
                        val posts = bridge.getTrending(count.coerceIn(1, 50))
                        call.respond<ApiResponse<List<ApiPost>>>(ApiResponse(success = true, data = posts.map { it.toApiPost() }))
                    }
                    get("/accounts") {
                        val accounts = bridge.getAccounts()
                        call.respond<ApiResponse<List<ApiAccount>>>(ApiResponse(success = true, data = accounts.map { it.toApiAccount() }))
                    }
                    get("/user/{id}/{host}") {
                        val userId = call.parameters["id"] ?: return@get call.respond<ApiResponse<String>>(ApiResponse(success = false, error = "missing user id"))
                        val userHost = call.parameters["host"] ?: return@get call.respond<ApiResponse<String>>(ApiResponse(success = false, error = "missing user host"))
                        val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 20
                        val posts = bridge.getUserTimeline(userId, userHost, count.coerceIn(1, 50))
                        call.respond<ApiResponse<List<ApiPost>>>(ApiResponse(success = true, data = posts.map { it.toApiPost() }))
                    }
                    get("/health") {
                        call.respond(mapOf("status" to "ok", "app" to "Flare MCP Server"))
                    }
                }
            }.start(wait = false)
            Log.d(TAG, "MCP Server started on http://127.0.0.1:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP Server", e)
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        scope.cancel()
    }

    companion object {
        private const val TAG = "FlareMcpServer"
    }
}

// --- API models ---

@Serializable
data class ApiPost(
    val id: String,
    val host: String,
    val platform: String,
    val content: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatar: String,
    val createdAt: Long,
    val mediaCount: Int,
    val sensitive: Boolean,
)

@Serializable
data class ApiUser(
    val id: String,
    val host: String,
    val name: String,
    val handle: String,
    val avatar: String,
    val banner: String,
    val description: String,
    val fansCount: Long,
    val followsCount: Long,
    val statusesCount: Long,
)

@Serializable
data class ApiAccount(
    val accountKeyId: String,
    val accountKeyHost: String,
    val platformType: String,
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
) {
    @Suppress("unused")
    constructor(success: Boolean, data: T) : this(success, data, null)
    @Suppress("unused")
    constructor(success: Boolean, error: String) : this(success, null, error)
}

// Converters
internal fun dev.dimension.flare.ui.model.UiTimelineV2.TimelinePostItem.toApiPost(): ApiPost {
    val p = displayPost
    return ApiPost(
        id = p.statusKey.id,
        host = p.statusKey.host,
        platform = p.platformType.name,
        content = p.content.raw,
        authorName = p.user?.name?.raw ?: "",
        authorHandle = p.user?.handle?.raw ?: "",
        authorAvatar = p.user?.avatar?.url ?: "",
        createdAt = p.createdAt.value.toEpochMilliseconds(),
        mediaCount = p.images.size,
        sensitive = p.sensitive,
    )
}

internal fun dev.dimension.flare.ui.model.UiProfile.toApiUser(): ApiUser = ApiUser(
    id = key.id,
    host = key.host,
    name = name.raw,
    handle = handle.raw,
    avatar = avatar?.url ?: "",
    banner = banner?.url ?: "",
    description = description?.raw ?: "",
    fansCount = matrices.fansCount,
    followsCount = matrices.followsCount,
    statusesCount = matrices.statusesCount,
)

internal fun dev.dimension.flare.ui.model.UiAccount.toApiAccount(): ApiAccount = ApiAccount(
    accountKeyId = accountKey.id,
    accountKeyHost = accountKey.host,
    platformType = platformType.name,
)
