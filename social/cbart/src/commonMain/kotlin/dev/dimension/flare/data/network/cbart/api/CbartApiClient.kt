package dev.dimension.flare.data.network.cbart.api

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.CbartCredential
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

private const val MAIN_API_BASE = "https://www.smlinzi.com/sml-app"
private const val API_HOST = "https://shenmatk.com"
private const val USER_AGENT = "Mozilla/5.0 (Android 16; Mobile; rv:152.0) Gecko/152.0 Firefox/152.0"

internal class CbartApiClient(
    private val credentialFlow: Flow<CbartCredential>,
) {
    private suspend fun credential(): CbartCredential? = credentialFlow.firstOrNull()

    private fun commonParams(cred: CbartCredential?): Map<String, String> {
        val params = mutableMapOf(
            "lang" to "cn",
            "app_version" to "7.0.01",
            "device" to "Flare-Android",
        )
        val token = cred?.apiToken ?: "ACF09D095C44ADD56B80FEE4A3A5BB3A"
        params["api_token"] = token
        cred?.uuid?.let { params["uuid"] = it }
        cred?.password?.let { params["password"] = it }
        return params
    }

    private fun httpClient(): HttpClient = ktorClient {}

    private suspend inline fun <reified T> postApi(
        path: String,
        extraParams: Map<String, String> = emptyMap(),
    ): T? {
        val cred = credential()
        val params = commonParams(cred) + extraParams
        val text = httpClient().post("$API_HOST/api/$path") {
            headers {
                append("User-Agent", USER_AGENT)
                append("Accept", "application/json, text/javascript, */*; q=0.01")
                append("Accept-Language", "zh-CN,en;q=0.9")
                append("X-Requested-With", "XMLHttpRequest")
            }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(params.map { (k, v) -> "$k=$v" }.joinToString("&"))
        }.bodyAsText()
        return tryParse(text)
    }

    private suspend inline fun <reified T> postMain(
        path: String,
        extraParams: Map<String, String> = emptyMap(),
    ): T? {
        val cred = credential()
        val params = commonParams(cred) + extraParams
        val text = httpClient().post("$MAIN_API_BASE/$path") {
            headers {
                append("User-Agent", USER_AGENT)
                append("Accept", "application/json, text/javascript, */*; q=0.01")
                append("Accept-Language", "zh-CN,en;q=0.9")
                append("X-Requested-With", "XMLHttpRequest")
            }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(params.map { (k, v) -> "$k=$v" }.joinToString("&"))
        }.bodyAsText()
        return tryParse(text)
    }

    private inline fun <reified T> tryParse(text: String): T? {
        return try { apiJson.decodeFromString<T>(text) } catch (_: Exception) { null }
    }

    suspend fun articleList(page: Int = 1, rowsPerPage: Int = 20): LzjArticleListResponse? = postApi(
        "articles.php", mapOf("page" to page.toString(), "rowsperpage" to rowsPerPage.toString()),
    )
    suspend fun init(deviceId: String): LzjInitResponse? = postMain(
        "init", mapOf("device_id" to deviceId),
    )
    suspend fun register(deviceId: String, username: String? = null, password: String? = null): LzjUserInfoResponse? = postMain(
        "register", buildMap {
            put("device_id", deviceId)
            username?.let { put("username", it) }
            password?.let { put("password", it) }
        },
    )
    suspend fun info(uuid: String): LzjUserInfoResponse? = postApi(
        "info.php", mapOf("uuid" to uuid),
    )
    suspend fun videoList(page: Int = 1, rowsPerPage: Int = 20): LzjVideoListResponse? = postApi(
        "video_list.php", mapOf("page" to page.toString(), "rowsperpage" to rowsPerPage.toString()),
    )
    suspend fun groupPics(page: Int = 1, rowsPerPage: Int = 20): LzjGroupPicsResponse? = postApi(
        "group_pics.php", mapOf("page" to page.toString(), "rowsperpage" to rowsPerPage.toString()),
    )
    suspend fun producerList(page: Int = 1, rowsPerPage: Int = 20): LzjProducerListResponse? = postApi(
        "producer.php", mapOf("page" to page.toString(), "rowsperpage" to rowsPerPage.toString()),
    )
    suspend fun getComments(videoId: Long, page: Int = 1): LzjCommentListResponse? = postApi(
        "video_comment.php", mapOf("act" to "get", "id" to videoId.toString(), "page" to page.toString()),
    )
    suspend fun addFavVideo(videoId: Long): LzjFavResponse? = postApi(
        "add_to_favorite.php", mapOf("videoId" to videoId.toString()),
    )
    suspend fun removeFavVideo(videoId: Long): LzjFavResponse? = postApi(
        "add_to_favorite.php", mapOf("videoId" to videoId.toString(), "act" to "remove"),
    )
    suspend fun getDailyFuli(): LzjFuliResponse? = postApi(
        "fuli.php", mapOf("act" to "getDaily"),
    )
    suspend fun getMessages(page: Int = 1): LzjMessageListResponse? = postApi(
        "get_message.php", mapOf("page" to page.toString()),
    )
    suspend fun getSetting(): LzjSettingResponse? = postApi("get_setting.php")
    suspend fun checkVersion(): LzjVersionCheckResponse? = postApi("check_version.php")
    suspend fun playlist(page: Int = 1, rowsPerPage: Int = 20): LzjPlaylistResponse? = postApi(
        "playlist.php", mapOf("page" to page.toString(), "rowsperpage" to rowsPerPage.toString()),
    )
    suspend fun tags(): LzjTagResponse? = postApi("tag.php", mapOf("sync" to "1"))
    suspend fun upvoteVideo(videoId: Long): LzjUpvoteResponse? = postApi(
        "add_video.php", mapOf("up_video" to "1", "video_id" to videoId.toString()),
    )
    suspend fun moneyHistory(): LzjMoneyHistoryResponse? = postApi("money_history.php")
}
