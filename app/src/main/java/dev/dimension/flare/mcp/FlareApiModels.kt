package dev.dimension.flare.mcp

import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Shared JSON response helpers for Intent IPC and MCP Server.
 */
internal object FlareApiModels {

    fun timelinePostToJson(post: UiTimelineV2.TimelinePostItem): String {
        val p = post.displayPost
        return buildJsonObject {
            put("id", p.statusKey.id)
            put("host", p.statusKey.host)
            put("platform", p.platformType.name)
            put("content", p.content.raw)
            put("author_name", p.user?.name?.raw ?: "")
            put("author_handle", p.user?.handle?.raw ?: "")
            put("author_avatar", p.user?.avatar?.url ?: "")
            put("created_at", p.createdAt.value.toEpochMilliseconds())
            put("media_count", p.images.size)
            put("sensitive", p.sensitive)
        }.toString()
    }

    fun timelineListToJson(posts: List<UiTimelineV2.TimelinePostItem>): String =
        buildJsonObject {
            put("items", buildJsonArray {
                posts.forEach { post ->
                    add(JsonPrimitive(timelinePostToJson(post)))
                }
            })
            put("count", posts.size)
        }.toString()

    fun userToJson(user: dev.dimension.flare.ui.model.UiProfile): String =
        buildJsonObject {
            put("id", user.key.id)
            put("host", user.key.host)
            put("name", user.name.raw)
            put("handle", user.handle.raw)
            put("avatar", user.avatar?.url ?: "")
            put("banner", user.banner?.url ?: "")
            put("description", user.description?.raw ?: "")
            put("fans_count", user.matrices.fansCount)
            put("follows_count", user.matrices.followsCount)
            put("statuses_count", user.matrices.statusesCount)
        }.toString()

    fun accountToJson(account: dev.dimension.flare.ui.model.UiAccount): String =
        buildJsonObject {
            put("account_key_id", account.accountKey.id)
            put("account_key_host", account.accountKey.host)
            put("platform_type", account.platformType.name)
        }.toString()

    fun accountListToJson(accounts: List<dev.dimension.flare.ui.model.UiAccount>): String =
        buildJsonObject {
            put("accounts", buildJsonArray {
                accounts.forEach { account ->
                    add(JsonPrimitive(accountToJson(account)))
                }
            })
        }.toString()
}
