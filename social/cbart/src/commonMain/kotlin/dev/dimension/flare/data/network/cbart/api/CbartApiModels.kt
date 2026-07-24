package dev.dimension.flare.data.network.cbart.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.Serializable

/**
 * 妖狐吧 API 通用响应格式
 * {code: 200, info: "...", data: {...}}
 * data 字段在无结果时返回 []，有结果时返回 {...}
 */
internal val apiJson = Json { ignoreUnknownKeys = true; isLenient = true }

// ==================== 通用响应包装 ====================

@Serializable
internal data class CbartApiResponse<T>(
    val code: Int,
    val info: String = "",
    val data: T? = null,
)

@Serializable
internal data class CbartSimpleResponse(
    val code: Int = 200,
    val info: String = "",
)

// ==================== 文章列表 (articles.php) ====================

@Serializable
internal data class LzjArticleListResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjArticleListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjArticleListData(
    val contents: List<LzjArticleItem> = emptyList(),
)

@Serializable
internal data class LzjArticleItem(
    val id: Long,
    val cid: Long? = null,
    val uid: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val content_short: String? = null,
    val posttime: String? = null,
    val updatetime: String? = null,
    val tag: String? = null,
    val views: Int? = null,
    val replyNum: Int? = null,
    val image: String? = null,
    val image_path: String? = null,
    val cn_name: String? = null,
    val username: String? = null,
    val url: String? = null,
    val is_public: Int? = null,
    val hasAttachment: Int? = null,
    val allowReply: Int? = null,
    val docs: String? = null,
)

// ==================== 用户信息 (info.php / register.php) ====================

@Serializable
internal data class LzjUserInfoResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjUserInfoData? = null,
)

@Serializable
internal data class LzjUserInfoData(
    val uid: Long? = null,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val money: Int? = null,
    val diamond: Int? = null,
    @SerialName("vip_end_time")
    val vipEndTime: String? = null,
    @SerialName("follower_num")
    val followerNum: Int? = null,
    @SerialName("email_verified")
    val emailVerified: Int? = null,
    val role: Int? = null,
    @SerialName("is_producer")
    val isProducer: Int? = null,
    val password: String? = null,
    @SerialName("plain_password")
    val plainPassword: String? = null,
)

// ==================== Init 响应 (smlinzi.com/sml-app/init) ====================

@Serializable
internal data class LzjInitResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjInitData? = null,
)

@Serializable
internal data class LzjInitData(
    val uid: Long? = null,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("sub_version_no")
    val subVersionNo: String? = null,
    @SerialName("chunk_size")
    val chunkSize: Int? = null,
    val currency: String? = null,
    val bonus: Int? = null,
    @SerialName("vip_cash")
    val vipCash: Int? = null,
    @SerialName("vip_gold")
    val vipGold: Int? = null,
    @SerialName("vip_prize_rate")
    val vipPrizeRate: Int? = null,
    @SerialName("gold_vip_price_arr")
    val goldVIPPriceArr: List<Int>? = null,
    @SerialName("diamond_vip_price_arr")
    val diamondVIPPriceArr: List<Int>? = null,
    @SerialName("enable_gold_vip")
    val enableGoldVIP: Int? = null,
    @SerialName("enable_diamond_vip")
    val enableDiamondVIP: Int? = null,
    @SerialName("alipay_enabled")
    val alipayEnabled: Int? = null,
    @SerialName("wechat_enabled")
    val wechatEnabled: Int? = null,
    @SerialName("topup_card_enabled")
    val topupCardEnabled: Int? = null,
    @SerialName("paypal_enabled")
    val paypalEnabled: Int? = null,
    @SerialName("hide_app_store_related")
    val hideAppStoreRelated: Int? = null,
    @SerialName("capcha_image_url")
    val capchaImageURL: String? = null,
    @SerialName("site_ip")
    val siteIP: String? = null,
    @SerialName("site_folder")
    val siteFolder: String? = null,
    @SerialName("enable_comment_email_verify")
    val enableCommentEmailVerify: Int? = null,
    @SerialName("g_max_downloading_num")
    val gMaxDownloadingNum: Int? = null,
)

// ==================== 视频列表 (video_list.php) ====================

@Serializable
internal data class LzjVideoListResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjVideoListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjVideoListData(
    val contents: List<LzjVideoItem> = emptyList(),
    @SerialName("total_num")
    val totalNum: Int? = null,
)

@Serializable
internal data class LzjVideoItem(
    val id: Long,
    val cid: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val price: Int? = null,
    @SerialName("price_diamond")
    val priceDiamond: Int? = null,
    @SerialName("purchased_num")
    val purchasedNum: String? = null,
    @SerialName("player_showed_num")
    val playerShowedNum: Int? = null,
    @SerialName("fav_num")
    val favNum: Int? = null,
    @SerialName("is_fav")
    val isFav: Int? = null,
    @SerialName("is_original")
    val isOriginal: Int? = null,
    @SerialName("is_featured")
    val isFeatured: Int? = null,
    @SerialName("is_discount")
    val isDiscount: Int? = null,
    @SerialName("can_watch_online")
    val canWatchOnline: Int? = null,
    @SerialName("duration_s")
    val durationS: Int? = null,
    val posttime: String? = null,
    val uid: Long? = null,
    val path: String? = null,
    val mPath: String? = null,
    val mobPath: String? = null,
    @SerialName("image_width")
    val imageWidth: Int? = null,
    @SerialName("image_height")
    val imageHeight: Int? = null,
    @SerialName("extra_text1")
    val extraText1: String? = null,
    @SerialName("extra_text2")
    val extraText2: String? = null,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("hasAttachment")
    val hasAttachment: Int? = null,
    val images: List<LzjImage>? = null,
    val owner: LzjVideoOwner? = null,
    val inbox: Int? = null,
    @SerialName("replyNum")
    val replyNum: Int? = null,
)

@Serializable
internal data class LzjImage(
    val id: Long,
    val path: String? = null,
    val mPath: String? = null,
    val mobPath: String? = null,
    val orgPath: String? = null,
    @SerialName("image_width")
    val imageWidth: Int? = null,
    @SerialName("image_height")
    val imageHeight: Int? = null,
)

@Serializable
internal data class LzjVideoOwner(
    val uid: Long? = null,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("follower_num")
    val followerNum: Int? = null,
    @SerialName("is_producer")
    val isProducer: Int? = null,
    @SerialName("is_followed")
    val isFollowed: Boolean? = null,
)

// ==================== 图片分组 (group_pics.php) ====================

@Serializable
internal data class LzjGroupPicsResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjGroupPicsData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjGroupPicsData(
    val contents: List<LzjPictureGroup> = emptyList(),
)

@Serializable
internal data class LzjPictureGroup(
    val id: Long,
    val title: String? = null,
    val content: String? = null,
    val posttime: String? = null,
    val uid: Long? = null,
    val path: String? = null,
    val mPath: String? = null,
    val images: List<LzjImage>? = null,
    val fav_num: Int? = null,
    val player_showed_num: Int? = null,
    val contentType: String? = null,
    val owner: LzjVideoOwner? = null,
)

// ==================== 作者/工作室列表 (producer.php) ====================

@Serializable
internal data class LzjProducerListResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjProducerListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjProducerListData(
    val contents: List<LzjProducerItem> = emptyList(),
)

@Serializable
internal data class LzjProducerItem(
    val id: Long,
    val uid: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("follower_num")
    val followerNum: Int? = null,
    @SerialName("content_num")
    val contentNum: Int? = null,
    @SerialName("is_followed")
    val isFollowed: Boolean? = null,
)

// ==================== 评论 (video_comment.php) ====================

@Serializable
internal data class LzjCommentListResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjCommentListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjCommentListData(
    val contents: List<LzjCommentItem> = emptyList(),
)

@Serializable
internal data class LzjCommentItem(
    val id: Long,
    val uid: Long? = null,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val content: String? = null,
    val posttime: String? = null,
    @SerialName("reply_num")
    val replyNum: Int? = null,
)

// ==================== 消息 (get_message.php) ====================

@Serializable
internal data class LzjMessageListResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjMessageListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjMessageListData(
    val contents: List<LzjMessageItem> = emptyList(),
)

@Serializable
internal data class LzjMessageItem(
    val id: Long,
    val uid: Long? = null,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    val title: String? = null,
    val content: String? = null,
    val viewed: Int? = null,
    @SerialName("post_time")
    val postTime: String? = null,
)

// ==================== 收藏 (add_to_favorite.php) ====================

@Serializable
internal data class LzjFavResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjFavData? = null,
)

@Serializable
internal data class LzjFavData(
    @SerialName("is_fav")
    val isFav: Int? = null,
    @SerialName("fav_num")
    val favNum: Int? = null,
)

// ==================== 福利 (fuli.php) ====================

@Serializable
internal data class LzjFuliResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjFuliData? = null,
)

@Serializable
internal data class LzjFuliData(
    val bonus: Int? = null,
    val diamond: Int? = null,
    val message: String? = null,
)

// ==================== 设置 (get_setting.php / change_setting.php) ====================

@Serializable
internal data class LzjSettingResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjSettingData? = null,
)

@Serializable
internal data class LzjSettingData(
    @SerialName("customer_service_info")
    val customerServiceInfo: String? = null,
    @SerialName("email_verified")
    val emailVerified: Int? = null,
    val money: Int? = null,
    val diamond: Int? = null,
    @SerialName("vip_end_time")
    val vipEndTime: String? = null,
    @SerialName("gold_vip_end_time")
    val goldVIPEndTime: String? = null,
    @SerialName("diamond_vip_end_time")
    val diamondVIPEndTime: String? = null,
)

// ==================== 版本检查 (check_version.php) ====================

@Serializable
internal data class LzjVersionCheckResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjVersionCheckData? = null,
)

@Serializable
internal data class LzjVersionCheckData(
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("sub_version_no")
    val subVersionNo: String? = null,
    @SerialName("chunk_size")
    val chunkSize: Int? = null,
    @SerialName("payment_gateway_url")
    val paymentGatewayURL: String? = null,
    @SerialName("alipay_enabled")
    val alipayEnabled: Int? = null,
    @SerialName("wechat_enabled")
    val wechatEnabled: Int? = null,
)

// ==================== 播放列表 (playlist.php) ====================

@Serializable
internal data class LzjPlaylistResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjPlaylistData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjPlaylistData(
    val contents: List<LzjPlaylistItem> = emptyList(),
)

@Serializable
internal data class LzjPlaylistItem(
    val id: Long,
    val title: String? = null,
    val video_id: Long? = null,
    val posttime: String? = null,
    val uid: Long? = null,
)

// ==================== 标签 (tag.php) ====================

@Serializable
internal data class LzjTagResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjTagData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjTagData(
    val categories: List<LzjCategory> = emptyList(),
    @SerialName("saved_num")
    val savedNum: Int? = null,
)

@Serializable
internal data class LzjCategory(
    val id: Long,
    val name: String? = null,
    @SerialName("cn_name")
    val cnName: String? = null,
    @SerialName("parent_id")
    val parentId: Long? = null,
    val type: String? = null,
    val image: String? = null,
)

// ==================== 顶视频 (add_video.php) ====================

@Serializable
internal data class LzjUpvoteResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: LzjUpvoteData? = null,
)

@Serializable
internal data class LzjUpvoteData(
    @SerialName("up_num")
    val upNum: Int? = null,
    val level: Int? = null,
)

// ==================== 消费记录 (money_history.php) ====================

@Serializable
internal data class LzjMoneyHistoryResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: LzjMoneyHistoryData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class LzjMoneyHistoryData(
    val contents: List<LzjMoneyRecord> = emptyList(),
)

@Serializable
internal data class LzjMoneyRecord(
    val id: Long,
    val amount: Int? = null,
    val type: String? = null,
    val note: String? = null,
    val posttime: String? = null,
)
