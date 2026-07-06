package dev.dimension.flare.mcp

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.android.ext.android.inject

/**
 * Intent-based IPC Service for RikkaHub integration.
 *
 * Protocol (mirrors Termux RUN_COMMAND):
 *   Action: "dev.dimension.flare.QUERY"
 *   Extra "operation": String
 *   Extra "params": JSON string
 *   Extra "pending_intent": PendingIntent callback
 *
 * Returns result via PendingIntent broadcast with extra "result" (JSON).
 */
class FlareQueryService : Service() {

    private val bridge: FlareBridge by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: run {
            stopSelf(startId); return START_NOT_STICKY
        }
        val paramsJson = intent.getStringExtra(EXTRA_PARAMS) ?: "{}"
        val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_PENDING_INTENT)
        }

        scope.launch {
            try {
                val result = processOperation(operation, paramsJson)
                pendingIntent?.let { pi ->
                    pi.send(this@FlareQueryService, 0, Intent().apply {
                        putExtra(EXTRA_RESULT, result)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Operation failed: $operation", e)
                pendingIntent?.let { pi ->
                    pi.send(this@FlareQueryService, 0, Intent().apply {
                        putExtra(EXTRA_ERROR, e.message ?: "Unknown error")
                    })
                }
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun processOperation(operation: String, paramsJson: String): String {
        val params = Json.parseToJsonElement(paramsJson).jsonObject
        return when (operation) {
            OP_HOME_TIMELINE -> {
                val count = params["count"]?.jsonPrimitive?.intOrNull ?: 20
                val posts = bridge.loadHomeTimeline(count)
                FlareApiModels.timelineListToJson(posts)
            }
            OP_SEARCH_POSTS -> {
                val query = params["query"]?.jsonPrimitive?.contentOrNull ?: return """{"error":"missing_query"}"""
                val count = params["count"]?.jsonPrimitive?.intOrNull ?: 20
                val posts = bridge.searchPosts(query, count)
                FlareApiModels.timelineListToJson(posts)
            }
            OP_SEARCH_USERS -> {
                val query = params["query"]?.jsonPrimitive?.contentOrNull ?: return """{"error":"missing_query"}"""
                val count = params["count"]?.jsonPrimitive?.intOrNull ?: 20
                val users = bridge.searchUsers(query, count)
                val usersStr = users.joinToString(",") { FlareApiModels.userToJson(it) }
                """{"users":[$usersStr],"count":${users.size}}"""
            }
            OP_NOTIFICATIONS -> {
                val count = params["count"]?.jsonPrimitive?.intOrNull ?: 20
                val posts = bridge.getNotifications(count)
                FlareApiModels.timelineListToJson(posts)
            }
            OP_TRENDING -> {
                val count = params["count"]?.jsonPrimitive?.intOrNull ?: 20
                val posts = bridge.getTrending(count)
                FlareApiModels.timelineListToJson(posts)
            }
            OP_ACCOUNTS -> {
                val accounts = bridge.getAccounts()
                FlareApiModels.accountListToJson(accounts)
            }
            OP_USER_TIMELINE -> {
                val userId = params["user_key_id"]?.jsonPrimitive?.contentOrNull ?: return """{"error":"missing_user_key_id"}"""
                val userHost = params["user_key_host"]?.jsonPrimitive?.contentOrNull ?: return """{"error":"missing_user_key_host"}"""
                val count = params["count"]?.jsonPrimitive?.intOrNull ?: 20
                val posts = bridge.getUserTimeline(userId, userHost, count)
                FlareApiModels.timelineListToJson(posts)
            }
            else -> """{"error":"unknown_operation","operation":"$operation"}"""
        }
    }

    companion object {
        private const val TAG = "FlareQueryService"
        const val ACTION_QUERY = "dev.dimension.flare.QUERY"
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_PARAMS = "params"
        const val EXTRA_PENDING_INTENT = "pending_intent"
        const val EXTRA_RESULT = "result"
        const val EXTRA_ERROR = "error"

        const val OP_HOME_TIMELINE = "get_home_timeline"
        const val OP_SEARCH_POSTS = "search_posts"
        const val OP_SEARCH_USERS = "search_users"
        const val OP_NOTIFICATIONS = "get_notifications"
        const val OP_TRENDING = "get_trending"
        const val OP_ACCOUNTS = "get_accounts"
        const val OP_USER_TIMELINE = "get_user_timeline"
    }
}
