package dev.dimension.flare.data.network.cbart.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 妖狐吧 API 实际响应格式（2026-07-24 实测）
 *
 * 所有响应都是扁平 JSON，没有 {code, data} 包装。
 * 成功时 success=1，失败时返回 error 字段。
 * id/uid 等字段是 String 类型（服务器返回字符串数字）。
 */
internal val apiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * linzijun.app Laravel API 通用响应包装
 * {code: 200, data: {contents: [...]}}
 */
@Serializable
internal data class LinzijunListResponse<T>(
    val code: Int? = null,
    val info: String? = null,
    val data: LinzijunListData<T>? = null,
)

@Serializable
internal data class LinzijunListData<T>(
    @SerialName("total_num") val totalNum: Int? = null,
    val contents: List<T> = emptyList(),
)

// ==================== 通用字段 ====================

/**
 * 所有 API 响应都包含的公共字段
 */
@Serializable
internal data class CommonFields(
    @SerialName("api_load_completed") val apiLoadCompleted: Int? = null,
    @SerialName("api_ycspq") val apiYcspq: Int? = null,
    @SerialName("amIInTheme") val amIInTheme: Int? = null,
    val success: Int? = null,
    val error: String? = null,
    val page: Int? = null,
    @SerialName("show_more") val showMore: Int? = null,
    val money: Int? = null,
    val diamond: Int? = null,
)

// ==================== 文章列表 (articles.php) ====================

@Serializable
internal data class LzjArticleListResponse(
    val success: Int? = null,
    val error: String? = null,
    val list: List<LzjArticleItem> = emptyList(),
    @SerialName("total_video_num") val totalVideoNum: String? = null,
    val page: Int? = null,
)

@Serializable
internal data class LzjArticleItem(
    val id: Int? = null,
    val cid: String? = null,
    val uid: String? = null,
    val title: String? = null,
    val content: String? = null,
    val posttime: String? = null,
    val updatetime: String? = null,
    val tag: String? = null,
    val views: String? = null,
    @SerialName("replyNum") val replyNum: String? = null,
    @SerialName("cn_name") val cnName: String? = null,
    val username: String? = null,
    val url: String? = null,
    @SerialName("is_public") val isPublic: String? = null,
    @SerialName("hasAttachment") val hasAttachment: String? = null,
    @SerialName("allowReply") val allowReply: String? = null,
    val docs: String? = null,
    val image: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
)

// ==================== 注册/登录 (register.php) ====================

@Serializable
internal data class LzjRegisterResponse(
    val success: Int? = null,
    val error: String? = null,
    val uid: String? = null,
    val username: String? = null,
    val password: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    val avatar: String? = null,
    @SerialName("email_verified") val emailVerified: Int? = null,
    val money: String? = null,
    val diamond: String? = null,
    val privacy: Int? = null,
    @SerialName("vip_end_time") val vipEndTime: String? = null,
    @SerialName("follower_num") val followerNum: String? = null,
    @SerialName("is_producer") val isProducer: String? = null,
)

// ==================== 用户信息 (info.php) ====================

/**
 * GET /api/user 用户信息（Laravel 站）
 */
@Serializable
internal data class CbartUserResponse(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val money: String? = null,
    val diamond: String? = null,
    @SerialName("email_verified_at") val emailVerifiedAt: String? = null,
)

// ==================== 登录响应 (login.php) ====================
// login.php 返回格式与 register.php 不同：uid 在 setting 对象内

@Serializable
internal data class LzjLoginResponse(
    val success: Int? = null,
    val error: String? = null,
    val setting: LzjLoginSetting? = null,
    val money: String? = null,
    val diamond: String? = null,
    @SerialName("email_verified") val emailVerified: Int? = null,
    @SerialName("purchased_video_ids") val purchasedVideoIds: String? = null,
)

@Serializable
internal data class LzjLoginSetting(
    val uid: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val email: String? = null,
    val privacy: String? = null,
    val password: String? = null,
)

@Serializable
internal data class LzjInfoResponse(
    val success: Int? = null,
    val error: String? = null,
    val uid: String? = null,
    val username: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val money: String? = null,
    val diamond: String? = null,
    @SerialName("vip_end_time") val vipEndTime: String? = null,
    @SerialName("follower_num") val followerNum: String? = null,
    @SerialName("email_verified") val emailVerified: Int? = null,
    @SerialName("is_producer") val isProducer: String? = null,
    val role: String? = null,
)

// ==================== 播放列表 (playlist.php) ====================

@Serializable
internal data class LzjPlaylistResponse(
    val success: Int? = null,
    val error: String? = null,
    val playlist: List<LzjPlaylistItem> = emptyList(),
    val page: Int? = null,
    @SerialName("show_more") val showMore: Int? = null,
    @SerialName("total_playlist_num") val totalPlaylistNum: Int? = null,
)

@Serializable
internal data class LzjPlaylistItem(
    val id: String,
    val uid: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("video_num") val videoNum: String? = null,
    val posttime: String? = null,
    @SerialName("update_time") val updateTime: String? = null,
    @SerialName("has_video") val hasVideo: List<LzjPlaylistVideoRef>? = null,
    val owner: LzjPlaylistOwner? = null,
    @SerialName("first_video_id") val firstVideoId: String? = null,
    val inbox: String? = null,
    @SerialName("has_en") val hasEn: String? = null,
    @SerialName("has_jp") val hasJp: String? = null,
    @SerialName("video_list") val videoList: List<LzjPlaylistVideoItem> = emptyList(),
)

@Serializable
internal data class LzjPlaylistVideoItem(
    val id: String? = null,
    val title: String? = null,
    val content: String? = null,
    val images: List<LzjPlaylistVideoImage> = emptyList(),
    @SerialName("playlist_type") val playlistType: String? = null,
    val purchased: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val posttime: String? = null,
    @SerialName("duration_s") val durationS: String? = null,
    @SerialName("fav_num") val favNum: String? = null,
    @SerialName("followable") val followable: String? = null,
    @SerialName("is_video") val isVideo: String? = null,
    val play: String? = null,
    val download: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    val path: String? = null,
    @SerialName("mPath") val mPath: String? = null,
    @SerialName("releasetime") val releaseTime: String? = null,
)

@Serializable
internal data class LzjPlaylistVideoImage(
    val id: String? = null,
    val path: String? = null,
    @SerialName("image_width") val imageWidth: String? = null,
    @SerialName("image_height") val imageHeight: String? = null,
    @SerialName("mPath") val mPath: String? = null,
    @SerialName("mobPath") val mobPath: String? = null,
)

@Serializable
internal data class LzjPlaylistVideoRef(
    val id: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("playlist_id") val playlistId: String? = null,
    val uid: String? = null,
    val inbox: String? = null,
    @SerialName("list_num") val listNum: String? = null,
    val type: String? = null,
    val posttime: String? = null,
)

@Serializable
internal data class LzjPlaylistOwner(
    val uid: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    @SerialName("vip_end_time") val vipEndTime: String? = null,
    @SerialName("diamond_vip_end_time") val diamondVipEndTime: String? = null,
    @SerialName("gold_vip_end_time") val goldVipEndTime: String? = null,
    @SerialName("is_producer") val isProducer: String? = null,
)

// ==================== 作者/工作室列表 (producer.php) ====================

@Serializable
internal data class LzjProducerListResponse(
    val success: Int? = null,
    val error: String? = null,
    val producers: List<LzjProducerItem> = emptyList(),
    @SerialName("total_producer_num") val totalProducerNum: Int? = null,
    @SerialName("show_more") val showMore: Int? = null,
    val page: Int? = null,
)

@Serializable
internal data class LzjProducerItem(
    val uid: String,
    val username: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    val avatar: String? = null,
    @SerialName("follower_num") val followerNum: String? = null,
    @SerialName("total_post") val totalPost: String? = null,
    @SerialName("total_earned") val totalEarned: String? = null,
    val hot: String? = null,
    val hentai: String? = null,
    val kindness: String? = null,
    val hide: String? = null,
    val updatetime: String? = null,
)

// ==================== 评论 (video_comment.php) ====================

@Serializable
internal data class LzjCommentListResponse(
    val success: Int? = null,
    val error: String? = null,
    val comments: List<LzjCommentItem> = emptyList(),
    val page: Int? = null,
    @SerialName("show_more") val showMore: Int? = null,
)

@Serializable
internal data class LzjCommentItem(
    val id: String,
    val uid: String? = null,
    val username: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val content: String? = null,
    val posttime: String? = null,
    @SerialName("reply_num") val replyNum: String? = null,
)

// ==================== 收藏 (add_to_favorite.php) ====================

@Serializable
internal data class LzjFavResponse(
    val success: Int? = null,
    val error: String? = null,
    @SerialName("is_fav") val isFav: Int? = null,
    @SerialName("fav_num") val favNum: String? = null,
)

// ==================== 福利 (fuli.php) ====================

@Serializable
internal data class LzjFuliResponse(
    val success: Int? = null,
    val error: String? = null,
    val bonus: String? = null,
    val diamond: String? = null,
    val message: String? = null,
)

// ==================== 设置 (get_setting.php) ====================

@Serializable
internal data class LzjSettingResponse(
    val success: Int? = null,
    val error: String? = null,
    @SerialName("customer_service_info") val customerServiceInfo: String? = null,
    @SerialName("email_verified") val emailVerified: Int? = null,
    val money: String? = null,
    val diamond: String? = null,
    @SerialName("vip_end_time") val vipEndTime: String? = null,
    @SerialName("gold_vip_end_time") val goldVipEndTime: String? = null,
    @SerialName("diamond_vip_end_time") val diamondVipEndTime: String? = null,
)

// ==================== 版本检查 (check_version.php) ====================

@Serializable
internal data class LzjVersionCheckResponse(
    val success: Int? = null,
    val error: String? = null,
    val version: String? = null,
    val description: String? = null,
    val url: String? = null,
    @SerialName("image_sub_server") val imageSubServer: String? = null,
    @SerialName("site_ip") val siteIp: String? = null,
    @SerialName("site_folder") val siteFolder: String? = null,
    @SerialName("currency") val currency: Int? = null,
    @SerialName("alipay_enabled") val alipayEnabled: Int? = null,
    @SerialName("wechat_enabled") val wechatEnabled: Int? = null,
    @SerialName("paypal_enabled") val paypalEnabled: Int? = null,
    @SerialName("topup_card_enabled") val topupCardEnabled: Int? = null,
    @SerialName("kefu_qq") val kefuQQ: String? = null,
    @SerialName("login_need_capcha") val loginNeedCapcha: Int? = null,
    @SerialName("capcha_image_url") val capchaImageUrl: String? = null,
    @SerialName("support_site") val supportSite: LzjSupportSite? = null,
    @SerialName("featured_video") val featuredVideo: List<LzjFeaturedVideoItem> = emptyList(),
)

@Serializable
internal data class LzjSupportSite(
    val url: String? = null,
    @SerialName("cn_name") val cnName: String? = null,
    @SerialName("en_name") val enName: String? = null,
)


// ==================== 精选内容 (featured_video) ====================

@Serializable
internal data class LzjFeaturedVideoItem(
    val id: String? = null,
    val title: String? = null,
    val images: List<String> = emptyList(),
    @SerialName("postBy") val postBy: String? = null,
    val avatar: String? = null,
    @SerialName("content_type") val contentType: String? = null,
)
// ==================== 消息 (get_message.php) ====================

@Serializable
internal data class LzjMessageListResponse(
    val success: Int? = null,
    val error: String? = null,
    val messages: List<LzjMessageItem> = emptyList(),
    val page: Int? = null,
    @SerialName("show_more") val showMore: Int? = null,
)

@Serializable
internal data class LzjMessageItem(
    val id: String,
    val uid: String? = null,
    val username: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    val title: String? = null,
    val content: String? = null,
    val viewed: String? = null,
    @SerialName("post_time") val postTime: String? = null,
)

// ==================== 标签 (tag.php) ====================

@Serializable
internal data class LzjTagResponse(
    val success: Int? = null,
    val error: String? = null,
    val categories: List<LzjCategory> = emptyList(),
    @SerialName("saved_num") val savedNum: String? = null,
)

@Serializable
internal data class LzjCategory(
    val id: String,
    val name: String? = null,
    @SerialName("cn_name") val cnName: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val type: String? = null,
    val image: String? = null,
)

// ==================== 顶视频 (add_video.php) ====================

@Serializable
internal data class LzjUpvoteResponse(
    val success: Int? = null,
    val error: String? = null,
    @SerialName("up_num") val upNum: String? = null,
    val level: String? = null,
)

// ==================== 消费记录 (money_history.php) ====================
// 注意：返回的是 HTML 片段（histHTML 字段），不是结构化 JSON

@Serializable
internal data class LzjMoneyHistoryResponse(
    val success: Int? = null,
    val error: String? = null,
    val money: String? = null,
    val diamond: String? = null,
    /** HTML 格式的消费记录（服务器返回 histHTML 字段） */
    @SerialName("histHTML") val historyHtml: String? = null,
)

// ==================== 关注作者 (user_originator_follow.php) ====================

@Serializable
internal data class LzjFollowResponse(
    val code: Int? = null,
    val info: String? = null,
)

// ==================== 视频列表 (video_list) ====================

@Serializable
internal data class LzjVideoImage(
    val id: String? = null,
    val path: String? = null,
    @SerialName("image_width") val imageWidth: String? = null,
    @SerialName("image_height") val imageHeight: String? = null,
    @SerialName("mPath") val mPath: String? = null,
    @SerialName("mobPath") val mobPath: String? = null,
    @SerialName("orgPath") val orgPath: String? = null,
)

@Serializable
internal data class LzjVideoListItem(
    val id: Int? = null,
    val title: String? = null,
    val price: Int? = null,
    @SerialName("price_diamond") val priceDiamond: Int? = null,
    @SerialName("purchased_num") val purchasedNum: String? = null,
    val posttime: String? = null,
    val uid: Int? = null,
    @SerialName("fav_num") val favNum: Int? = null,
    @SerialName("is_original") val isOriginal: Int? = null,
    @SerialName("has_preview") val hasPreview: Int? = null,
    @SerialName("has_repo") val hasRepo: Int? = null,
    @SerialName("has_playlist") val hasPlaylist: Int? = null,
    @SerialName("is_featured") val isFeatured: Int? = null,
    @SerialName("has_wangpan") val hasWangpan: Int? = null,
    @SerialName("image_width") val imageWidth: Int? = null,
    @SerialName("image_height") val imageHeight: Int? = null,
    val images: List<LzjVideoImage> = emptyList(),
    @SerialName("duration_hr") val durationHr: String? = null,
    @SerialName("price_desc") val priceDesc: String? = null,
    @SerialName("is_new") val isNew: Int? = null,
    @SerialName("content_short") val contentShort: String? = null,
    @SerialName("can_watch_online") val canWatchOnline: Int? = null,
    val owner: LzjVideoOwner? = null,
)

@Serializable
internal data class LzjVideoOwner(
    val uid: Int? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val avatar: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("nick_name") val nickName: String? = null,
    @SerialName("follower_num") val followerNum: Int? = null,
    @SerialName("is_followed") val isFollowed: Boolean? = null,
)


// ==================== 视频详情 (video_detail) ====================

@Serializable
internal data class LzjVideoListData(
    @SerialName("total_num") val totalNum: Int? = null,
    val contents: List<LzjVideoListItem> = emptyList(),
)

@Serializable
internal data class LzjVideoListResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjVideoListData? = null,
)

@Serializable
internal data class LzjVideoDetailResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjVideoDetailData? = null,
)

@Serializable
internal data class LzjVideoDetailData(
    @SerialName("userfiles_server") val userfilesServer: String? = null,
    val contents: List<LzjVideoDetailItem> = emptyList(),
    @SerialName("is_desktop") val isDesktop: Boolean? = null,
)

@Serializable
internal data class LzjVideoDetailItem(
    val id: Int? = null,
    val cid: String? = null,
    val title: String? = null,
    val content: String? = null,
    @SerialName("supplyment_content") val supplymentContent: String? = null,
    val price: Int? = null,
    @SerialName("price_diamond") val priceDiamond: Int? = null,
    @SerialName("price_desc") val priceDesc: String? = null,
    @SerialName("purchased_num") val purchasedNum: Int? = null,
    @SerialName("fav_num") val favNum: Int? = null,
    val replyNum: Int? = null,
    val posttime: String? = null,
    val uid: Int? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("duration_hr") val durationHr: String? = null,
    val size: Long? = null,
    @SerialName("extra_text2") val extraText2: String? = null,
    @SerialName("is_original") val isOriginal: Int? = null,
    @SerialName("has_preview") val hasPreview: Int? = null,
    @SerialName("has_repo") val hasRepo: Int? = null,
    @SerialName("has_playlist") val hasPlaylist: Int? = null,
    @SerialName("can_watch_online") val canWatchOnline: Int? = null,
    @SerialName("is_featured") val isFeatured: Int? = null,
    @SerialName("is_discount") val isDiscount: Int? = null,
    @SerialName("has_wangpan") val hasWangpan: Int? = null,
    @SerialName("image_width") val imageWidth: Int? = null,
    @SerialName("image_height") val imageHeight: Int? = null,
    val path: String? = null,
    @SerialName("mPath") val mPath: String? = null,
    val images: List<LzjVideoImage> = emptyList(),
    @SerialName("cdn_name") val cdnName: List<String> = emptyList(),
    val locked: Int? = null,
    @SerialName("is_public") val isPublic: Int? = null,
    @SerialName("watermarked") val watermarked: Int? = null,
    @SerialName("storyboard_file") val storyboardFile: String? = null,
    @SerialName("repo_download_sent") val repoDownloadSent: Int? = null,
    val docs: String? = null,
    @SerialName("storage_life") val storageLife: String? = null,
)
