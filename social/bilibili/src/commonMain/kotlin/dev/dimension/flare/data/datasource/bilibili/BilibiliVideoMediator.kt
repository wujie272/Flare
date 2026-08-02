package dev.dimension.flare.data.datasource.bilibili

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bilibili.BilibiliService
import dev.dimension.flare.data.network.bilibili.PlayUrlData
import kotlinx.serialization.json.jsonPrimitive
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

/**
 * 加载视频详情 + 播放地址
 * 第一页：视频信息 + 播放地址
 * 后续页：评论列表
 */
@OptIn(ExperimentalPagingApi::class)
internal class BilibiliVideoDetailMediator(
    private val service: BilibiliService,
    private val accountKey: MicroBlogKey,
    private val bvid: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bilibili_video_detail_${bvid}_${accountKey.id}"
    override val supportPrepend: Boolean = false
    private var cid: Long = 0
    private var aid: Long = 0
    private var videoInfoRendered: Boolean = false
    private val commentsLoader = BilibiliCommentsLoader(
        service = service,
        accountKey = accountKey,
        bvid = bvid,
    )

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        // 第一页：加载视频信息 + 播放地址
        if (!videoInfoRendered && request is PagingRequest.Refresh) {
            videoInfoRendered = true

            // 获取视频信息
            val videoJson = service.getVideoInfo(bvid)
            if (videoJson == null) {
                return PagingResult(endOfPaginationReached = true)
            }
            val videoInfo = BilibiliVideoInfo.fromJson(videoJson) ?: return PagingResult(endOfPaginationReached = true)
            cid = videoInfo.cid
            aid = videoJson["aid"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

            // 获取播放地址
            val playUrl: String? = try {
                if (cid > 0) {
                    service.getPlayUrl(bvid = bvid, cid = cid)
                } else {
                    null
                }
            } catch (_: Exception) { null }

            val videoItem = videoInfo.toUiTimelineV2(accountKey = accountKey, playUrl = playUrl)

            return PagingResult(
                data = listOf(videoItem),
                nextKey = "comments_1",
            )
        }

        // 后续页：评论列表
        val page = when (request) {
            PagingRequest.Refresh -> 1
            is PagingRequest.Append -> {
                val key = request.nextKey
                if (key.startsWith("comments_")) {
                    key.removePrefix("comments_").toIntOrNull() ?: 1
                } else {
                    1
                }
            }
        }

        val result = commentsLoader.load(pageSize = pageSize, request = PagingRequest.Append(nextKey = page.toString()))
        return PagingResult(
            data = result.data,
            nextKey = if (result.nextKey == null) null else "comments_${page + 1}",
        )
    }
}
