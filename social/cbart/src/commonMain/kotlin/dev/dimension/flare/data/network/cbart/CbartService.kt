package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.data.network.cbart.api.*
import dev.dimension.flare.data.platform.CbartCredential
import kotlinx.coroutines.flow.Flow

internal class CbartService(
    credentialFlow: Flow<CbartCredential>,
) {
    val api = CbartApiClient(credentialFlow = credentialFlow)

    // ==================== 文章/公告 ====================

    suspend fun fetchArticles(page: Int = 1, limit: Int = 20): List<LzjArticleItem> {
        val response = api.articleList(page = page, rowsPerPage = limit)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 初始化 & 注册 ====================

    suspend fun initDevice(deviceId: String): LzjInitData? {
        return api.init(deviceId = deviceId)?.data
    }

    suspend fun registerDevice(deviceId: String, username: String? = null, password: String? = null): LzjUserInfoData? {
        return api.register(deviceId = deviceId, username = username, password = password)?.data
    }

    // ==================== 用户信息 ====================

    suspend fun fetchUserInfo(uuid: String): LzjUserInfoData? {
        return api.info(uuid = uuid)?.data
    }

    // ==================== 视频列表 ====================

    suspend fun fetchVideos(page: Int = 1, limit: Int = 20): List<LzjVideoItem> {
        val response = api.videoList(page = page, rowsPerPage = limit)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 图片分组 ====================

    suspend fun fetchPictureGroups(page: Int = 1, limit: Int = 20): List<LzjPictureGroup> {
        val response = api.groupPics(page = page, rowsPerPage = limit)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 作者列表 ====================

    suspend fun fetchProducers(page: Int = 1, limit: Int = 20): List<LzjProducerItem> {
        val response = api.producerList(page = page, rowsPerPage = limit)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 评论 ====================

    suspend fun fetchComments(videoId: Long, page: Int = 1): List<LzjCommentItem> {
        val response = api.getComments(videoId = videoId, page = page)
        return response?.data?.contents ?: emptyList()
    }

    // ==================== 收藏 ====================

    suspend fun toggleVideoFav(videoId: Long): Boolean? {
        // 先尝试收藏，如果已经收藏了则取消
        val response = api.addFavVideo(videoId = videoId)
        return response?.code == 200
    }

    // ==================== 视频详情 ====================

    suspend fun fetchVideoById(videoId: Long): LzjVideoItem? {
        val response = api.videoList(page = 1, rowsPerPage = 50)
        return response?.data?.contents?.firstOrNull { it.id == videoId }
    }

    // ==================== 福利 ====================

    suspend fun fetchDailyFuli(): LzjFuliData? {
        return api.getDailyFuli()?.data
    }

    // ==================== 消息 ====================

    suspend fun fetchMessages(page: Int = 1): List<LzjMessageItem> {
        return api.getMessages(page = page)?.data?.contents ?: emptyList()
    }

    // ==================== 用户设置 ====================

    suspend fun fetchSettings(): LzjSettingData? {
        return api.getSetting()?.data
    }

    // ==================== 版本检查 ====================

    suspend fun checkVersion(): LzjVersionCheckData? {
        return api.checkVersion()?.data
    }

    // ==================== 播放列表 ====================

    suspend fun fetchPlaylists(page: Int = 1, limit: Int = 20): List<LzjPlaylistItem> {
        return api.playlist(page = page, rowsPerPage = limit)?.data?.contents ?: emptyList()
    }

    // ==================== 标签 ====================

    suspend fun fetchTags(): LzjTagData? {
        return api.tags()?.data
    }

    // ==================== 顶视频 ====================

    suspend fun upvoteVideo(videoId: Long): Boolean {
        return api.upvoteVideo(videoId = videoId)?.code == 200
    }

    // ==================== 消费记录 ====================

    suspend fun fetchMoneyHistory(): List<LzjMoneyRecord> {
        return api.moneyHistory()?.data?.contents ?: emptyList()
    }
}
