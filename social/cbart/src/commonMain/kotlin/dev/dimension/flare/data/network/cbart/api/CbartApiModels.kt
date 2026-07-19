package dev.dimension.flare.data.network.cbart.api

import kotlinx.serialization.SerialName

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.Serializable


/**
 * 这个 API 的 data 字段在无结果时返回 []（空数组），
 * 有结果时返回 {...}（对象），所以不能用 @Serializable 直接映射。
 * 改为 JsonElement 后手动解析。
 */
internal val apiJson = Json { ignoreUnknownKeys = true; isLenient = true }

// ==================== 通用响应包装 ====================

/**
 * Laravel API 统一响应格式：
 * {code: 200, info: "", data: {...}}
 */
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

@Serializable
internal data class CbartGenericResponse(
    val message: String? = null,
)

// ==================== 内容列表 (Content) ====================

@Serializable
internal data class CbartContentListResponse(
    val code: Int,
    val info: String = "",
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: CbartContentListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class CbartContentListData(
    val contents: List<CbartContentItem> = emptyList(),
)

@Serializable
internal data class CbartContentItem(
    val id: Long,
    val cid: Long? = null,
    val vid: String? = null,
    @SerialName("live_id")
    val liveId: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val price: Int? = null,
    @SerialName("price_diamond")
    val priceDiamond: Int? = null,
    @SerialName("purchased_num")
    val purchasedNum: String? = null,
    @SerialName("player_showed_num")
    val playerShowedNum: Int? = null,
    @SerialName("page_loaded_num")
    val pageLoadedNum: Int? = null,
    @SerialName("storage_life")
    val storageLife: String? = null,
    val posttime: String? = null,
    val updatetime: String? = null,
    val uid: Long? = null,
    val inbox: Int? = null,
    @SerialName("replyNum")
    val replyNum: Int? = null,
    @SerialName("fav_num")
    val favNum: Int? = null,
    @SerialName("hasAttachment")
    val hasAttachment: Int? = null,
    val docs: String? = null,
    @SerialName("extra_text1")
    val extraText1: String? = null,
    @SerialName("extra_text2")
    val extraText2: String? = null,
    @SerialName("is_original")
    val isOriginal: Int? = null,
    @SerialName("is_public")
    val isPublic: Int? = null,
    @SerialName("is_featured")
    val isFeatured: Int? = null,
    @SerialName("is_discount")
    val isDiscount: Int? = null,
    @SerialName("is_archived")
    val isArchived: Int? = null,
    @SerialName("is_user_blocked")
    val isUserBlocked: Boolean? = null,
    val path: String? = null,
    val mPath: String? = null,
    @SerialName("image_width")
    val imageWidth: Int? = null,
    @SerialName("image_height")
    val imageHeight: Int? = null,
    val images: List<CbartContentImage>? = null,
    val tiers: List<CbartTierRef>? = null,
    @SerialName("tier_id_arr")
    val tierIdArr: List<Long>? = null,
)

@Serializable
internal data class CbartContentImage(
    val id: Long,
    val path: String,
    @SerialName("image_width")
    val imageWidth: Int? = null,
    @SerialName("image_height")
    val imageHeight: Int? = null,
    val mPath: String? = null,
    val mobPath: String? = null,
    val orgPath: String? = null,
)

@Serializable
internal data class CbartTierRef(
    val id: Long? = null,
    val name: String? = null,
)

// ==================== 工作室列表 (Studio) ====================

@Serializable
internal data class CbartStudioListResponse(
    val code: Int,
    val info: String = "",
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: CbartStudioListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class CbartStudioListData(
    @SerialName("total_num")
    val totalNum: Int? = null,
    val contents: List<CbartStudioItem> = emptyList(),
    val currency: JsonElement? = null,
    @SerialName("saved_filters")
    val savedFilters: JsonElement? = null,
)

@Serializable
internal data class CbartStudioItem(
    val id: Long,
    val uid: Long,
    val name: String? = null,
    val description: String? = null,
    @SerialName("cover_picture")
    val coverPicture: String? = null,
    @SerialName("supporter_num")
    val supporterNum: Int? = null,
    val inbox: Int? = null,
    @SerialName("is_public")
    val isPublic: Int? = null,
    val updatetime: String? = null,
    val posttime: String? = null,
    @SerialName("uof_posttime")
    val uofPosttime: String? = null,
    @SerialName("follower_num")
    val followerNum: Int? = null,
    val owner: CbartUser? = null,
    @SerialName("cover_picture_url")
    val coverPictureUrl: String? = null,
    @SerialName("is_followed")
    val isFollowed: Boolean? = null,
    @SerialName("content_num")
    val contentNum: CbartContentCount? = null,
)

@Serializable
internal data class CbartContentCount(
    val tier: Int? = null,
    val video: Int? = null,
    val album: Int? = null,
    val fiction: Int? = null,
    val blog: Int? = null,
)

// ==================== 用户 ====================

@Serializable
internal data class CbartUser(
    val uid: Long,
    val username: String? = null,
    val lang: String? = null,
    @SerialName("email_verified")
    val emailVerified: Int? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val role: Int? = null,
    val money: Int? = null,
    val diamond: Int? = null,
    @SerialName("follower_num")
    val followerNum: Int? = null,
    @SerialName("is_producer")
    val isProducer: Int? = null,
    @SerialName("is_performer")
    val isPerformer: Int? = null,
    @SerialName("vip_end_time")
    val vipEndTime: String? = null,
)

// ==================== 博客列表 (Blog) ====================

@Serializable
internal data class CbartBlogListResponse(
    val code: Int,
    val info: String = "",
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: CbartBlogListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class CbartBlogListData(
    @SerialName("total_num")
    val totalNum: Int? = null,
    @SerialName("total_public_num")
    val totalPublicNum: Int? = null,
    @SerialName("content_num")
    val contentNum: Int? = null,
    val contents: List<CbartBlogItem> = emptyList(),
)

@Serializable
internal data class CbartBlogItem(
    val id: Long,
    val uid: Long? = null,
    @SerialName("cover_picture_id")
    val coverPictureId: Long? = null,
    val title: String? = null,
    @SerialName("studio_id")
    val studioId: Long? = null,
    @SerialName("studio_tier_id")
    val studioTierId: Long? = null,
    @SerialName("content_id")
    val contentId: Long? = null,
    @SerialName("content_type")
    val contentType: String? = null,
    val inbox: Int? = null,
    val posttime: String? = null,
    val locked: Int? = null,
    val comment: List<String>? = null,
    @SerialName("content_short")
    val contentShort: String? = null,
    @SerialName("cover_picture")
    val coverPicture: List<String>? = null,
    @SerialName("content_title")
    val contentTitle: String? = null,
    @SerialName("content_price_gold")
    val contentPriceGold: Int? = null,
    @SerialName("content_price_diamond")
    val contentPriceDiamond: Int? = null,
    @SerialName("content_locked")
    val contentLocked: Int? = null,
)

// ==================== Tier 列表 ====================

@Serializable
internal data class CbartTierListResponse(
    val code: Int,
    val info: String = "",
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: CbartTierListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class CbartTierListData(
    @SerialName("is_my_studio")
    val isMyStudio: Boolean? = null,
    val title: String? = null,
    val studio: CbartTierStudio? = null,
    val contents: List<CbartTierItem> = emptyList(),
    val owner: CbartUser? = null,
)

@Serializable
internal data class CbartTierStudio(
    val id: Long? = null,
    val uid: Long? = null,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
internal data class CbartTierItem(
    val id: Long,
    @SerialName("studio_id")
    val studioId: Long? = null,
    val uid: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Int? = null,
    @SerialName("supporter_num")
    val supporterNum: Int? = null,
    @SerialName("cover_picture")
    val coverPicture: String? = null,
    val locked: Int? = null,
    @SerialName("num_arr")
    val numArr: CbartContentCount? = null,
)

// ==================== 消息列表 ====================

@Serializable
internal data class CbartMessageListResponse(
    val code: Int,
    val info: String = "",
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: CbartMessageListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class CbartMessageListData(
    val contents: List<CbartMessageItem> = emptyList(),
)

@Serializable
internal data class CbartMessageItem(
    val uid: Long,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    val title: String? = null,
    val content: String? = null,
    val viewed: Int? = null,
    @SerialName("post_time")
    val postTime: String? = null,
    val user: CbartUser? = null,
)

// ==================== 文章列表 ====================

@Serializable
internal data class CbartArticleListResponse(
    val code: Int? = null,
    val info: String? = null,
    @SerialName("data")
    private val rawData: JsonElement? = null,
) {
    val data: CbartArticleListData?
        get() = (rawData as? JsonObject)?.let { apiJson.decodeFromJsonElement(it) }
}

@Serializable
internal data class CbartArticleListData(
    val contents: List<CbartArticleItem>? = null,
)

@Serializable
internal data class CbartArticleItem(
    val id: Long? = null,
    val cid: Long? = null,
    val uid: Long? = null,
    val owner: Long? = null,
    val posttime: String? = null,
    val updatetime: String? = null,
    val title: String? = null,
    val content: String? = null,
    val tag: String? = null,
    val views: Int? = null,
    val inbox: Int? = null,
    val hasAttachment: Int? = null,
    val allowReply: Int? = null,
    val replyNum: Int? = null,
    val docs: String? = null,
    val url: String? = null,
    val is_public: Int? = null,
    val has_en: Int? = null,
    val has_jp: Int? = null,
    val cn_name: String? = null,
    val column_id: Long? = null,
    val username: String? = null,
    val content_short: String? = null,
    val image: String? = null,
    val image_path: String? = null,
)

// ==================== 视频列表 (Video) ====================

@Serializable
internal data class CbartVideoListResponse(
    val code: Int,
    val info: String = "",
    @SerialName("data")
    val data: CbartVideoListData? = null,
)

@Serializable
internal data class CbartVideoListData(
    @SerialName("total_num")
    val totalNum: Int? = null,
    val contents: List<CbartVideoItem> = emptyList(),
    @SerialName("time_spent")
    val timeSpent: Int? = null,
)

@Serializable
internal data class CbartVideoItem(
    val id: Long,
    val title: String? = null,
    val price: Int? = null,
    @SerialName("price_diamond")
    val priceDiamond: Int? = null,
    @SerialName("purchased_num")
    val purchasedNum: String? = null,
    val posttime: String? = null,
    val releasetime: String? = null,
    val updatenum: Int? = null,
    val uid: Long? = null,
    val inbox: Int? = null,
    @SerialName("fav_num")
    val favNum: Int? = null,
    @SerialName("extra_text2")
    val extraText2: String? = null,
    @SerialName("is_original")
    val isOriginal: Int? = null,
    @SerialName("has_repo")
    val hasRepo: Int? = null,
    @SerialName("has_preview")
    val hasPreview: Int? = null,
    @SerialName("has_tier")
    val hasTier: Int? = null,
    @SerialName("can_watch_online")
    val canWatchOnline: Int? = null,
    @SerialName("is_featured")
    val isFeatured: Int? = null,
    @SerialName("is_discount")
    val isDiscount: Int? = null,
    @SerialName("is_archived")
    val isArchived: Int? = null,
    @SerialName("is_user_blocked")
    val isUserBlocked: Boolean? = null,
    @SerialName("image_width")
    val imageWidth: Int? = null,
    @SerialName("image_height")
    val imageHeight: Int? = null,
    val images: List<CbartContentImage>? = null,
)

// ==================== 最新内容 (New Content) ====================

@Serializable
internal data class CbartNewContentListResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: CbartNewContentListData? = null,
)

@Serializable
internal data class CbartNewContentListData(
    @SerialName("new_content")
    val newContent: List<CbartNewContentItem>? = null,
)

@Serializable
internal data class CbartNewContentItem(
    val id: Long,
    @SerialName("content_id")
    val contentId: Long,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("is_public")
    val isPublic: Int? = null,
    @SerialName("is_featured")
    val isFeatured: Int? = null,
    val posttime: String? = null,
    @SerialName("cover_picture")
    val coverPicture: String? = null,
    val ratio: Int? = null,
    val uid: Long? = null,
    val title: String? = null,
    val summary: String? = null,
    val username: String? = null,
)

// ==================== 内容类型 ====================

// ==================== 视频详情 ====================

/**
 * 从 /video/detail?id=xxx 页面 HTML 中 videoDetailJSON.list[0] 提取的完整视频详情
 */
@Serializable
internal data class CbartVideoDetailItem(
    val id: Long,
    val cid: Long? = null,
    val vid: String? = null,
    @SerialName("live_id")
    val liveId: Long? = null,
    @SerialName("user_script_id")
    val userScriptId: Long? = null,
    val title: String? = null,
    val content: String? = null,
    @SerialName("supplyment_content")
    val supplymentContent: String? = null,
    val price: Int? = null,
    @SerialName("price_diamond")
    val priceDiamond: Int? = null,
    @SerialName("purchased_num")
    val purchasedNum: String? = null,
    @SerialName("player_showed_num")
    val playerShowedNum: Int? = null,
    @SerialName("page_loaded_num")
    val pageLoadedNum: Int? = null,
    @SerialName("storage_life")
    val storageLife: String? = null,
    val posttime: String? = null,
    val updatetime: String? = null,
    val releasetime: String? = null,
    val updatenum: Int? = null,
    val uid: Long? = null,
    val inbox: Int? = null,
    @SerialName("replyNum")
    val replyNum: Int? = null,
    @SerialName("fav_num")
    val favNum: Int? = null,
    @SerialName("hasAttachment")
    val hasAttachment: Int? = null,
    val docs: String? = null,
    @SerialName("extra_text1")
    val extraText1: String? = null,
    @SerialName("extra_text2")
    val extraText2: String? = null,
    @SerialName("duration_s")
    val durationS: Int? = null,
    val size: Long? = null,
    @SerialName("play_only")
    val playOnly: Int? = null,
    @SerialName("is_earn_more_exp")
    val isEarnMoreExp: Int? = null,
    @SerialName("is_original")
    val isOriginal: Int? = null,
    @SerialName("has_repo")
    val hasRepo: Int? = null,
    @SerialName("has_cn_repo")
    val hasCnRepo: Int? = null,
    @SerialName("has_us_repo")
    val hasUsRepo: Int? = null,
    @SerialName("has_preview")
    val hasPreview: Int? = null,
    @SerialName("has_cn_oss")
    val hasCnOss: Int? = null,
    @SerialName("has_us_oss")
    val hasUsOss: Int? = null,
    @SerialName("has_playlist")
    val hasPlaylist: Int? = null,
    @SerialName("has_tier")
    val hasTier: Int? = null,
    @SerialName("repo_download_sent")
    val repoDownloadSent: Int? = null,
    @SerialName("is_public")
    val isPublic: Int? = null,
    @SerialName("can_watch_online")
    val canWatchOnline: Int? = null,
    @SerialName("has_en")
    val hasEn: Int? = null,
    @SerialName("has_jp")
    val hasJp: Int? = null,
    @SerialName("is_featured")
    val isFeatured: Int? = null,
    @SerialName("is_discount")
    val isDiscount: Int? = null,
    @SerialName("is_archived")
    val isArchived: Int? = null,
    val watermarked: Int? = null,
    @SerialName("this_has_loop")
    val thisHasLoop: Int? = null,
    @SerialName("is_user_blocked")
    val isUserBlocked: Boolean? = null,
    @SerialName("has_wangpan")
    val hasWangpan: Int? = null,
    val path: String? = null,
    val mPath: String? = null,
    @SerialName("image_width")
    val imageWidth: Int? = null,
    @SerialName("image_height")
    val imageHeight: Int? = null,
    val images: List<CbartContentImage>? = null,
    @SerialName("is_fav")
    val isFav: Boolean? = null,
    val comment: List<CbartVideoComment>? = null,
    val owner: CbartVideoOwner? = null,
)

@Serializable
internal data class CbartVideoOwner(
    val uid: Long,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("is_followed")
    val isFollowed: Boolean? = null,
    @SerialName("follower_num")
    val followerNum: Int? = null,
)

@Serializable
internal data class CbartVideoComment(
    val id: Long? = null,
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
    val inbox: Int? = null,
)

// ==================== 视频评论提交响应 ====================

@Serializable
internal data class CbartVideoCommentAddResponse(
    val code: Int? = null,
    val info: String? = null,
    val data: CbartVideoCommentAddData? = null,
)

@Serializable
internal data class CbartVideoCommentAddData(
    val comment: CbartVideoComment? = null,
)

/**
 * 为 UI 展示区分内容类型
 */
internal enum class CbartApiContentType(val label: String) {
    Video(" Video"),
    Picture(" Gallery"),
    Fiction("📖 Fiction"),
    Blog("📝 Blog"),
    Unknown("❓");

    companion object {
        fun fromContentType(ct: String?): CbartApiContentType = when {
            ct == null || ct == "video" -> Video
            ct == "album" || ct == "picture" -> Picture
            ct == "fiction" -> Fiction
            ct == "blog" -> Blog
            else -> Unknown
        }
    }
}
