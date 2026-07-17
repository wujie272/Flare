package dev.dimension.flare.data.network.cbart.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ==================== 通用响应 ====================

@Serializable
internal data class CbartApiResponse<T>(
    val success: Int? = null,
    val data: T? = null,
    val msg: String? = null,
    @SerialName("api_load_completed")
    val apiLoadCompleted: Int? = null,
    @SerialName("amIInTheme")
    val amIInTheme: Int? = null,
)

@Serializable
internal data class CbartSimpleResponse(
    val success: Int? = null,
    val msg: String? = null,
)

// ==================== 统计排行 ====================

@Serializable
internal data class CbartStatsResponse(
    val success: Int? = null,
    val stats: List<CbartStatsItem>? = null,
    @SerialName("api_load_completed")
    val apiLoadCompleted: Int? = null,
)

@Serializable
internal data class CbartStatsItem(
    val uid: String,
    val username: String? = null,
    @SerialName("nick_name")
    val nickName: String? = null,
    val avatar: String? = null,
    @SerialName("total_post")
    val totalPost: String? = null,
    @SerialName("total_earned")
    val totalEarned: String? = null,
    @SerialName("follower_num")
    val followerNum: String? = null,
    val hot: String? = null,
    val rank: Int? = null,
    @SerialName("rank_str")
    val rankStr: String? = null,
    val studio: CbartStudio? = null,
)

@Serializable
internal data class CbartStudio(
    val id: String? = null,
    val uid: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("cover_picture")
    val coverPicture: String? = null,
    @SerialName("supporter_num")
    val supporterNum: String? = null,
)

// ==================== 收藏 ====================

@Serializable
internal data class CbartFavoriteResponse(
    val success: Int? = null,
    val msg: String? = null,
    /** 收藏状态：1=已收藏, 0=未收藏 */
    val favorite: Int? = null,
)

// ==================== 标签 ====================

@Serializable
internal data class CbartTagListResponse(
    val success: Int? = null,
    val data: List<CbartTag>? = null,
)

@Serializable
internal data class CbartTag(
    val id: String? = null,
    val name: String? = null,
    @SerialName("tag_type")
    val tagType: String? = null,
    val count: String? = null,
)

// ==================== 视频 ====================

@Serializable
internal data class CbartVideoResponse(
    val success: Int? = null,
    val url: String? = null,
    val type: String? = null,
    val msg: String? = null,
)

// ==================== 内容条目（从首页 HTML / 用户主页解析） ====================

internal data class CbartApiContentItem(
    val id: String,
    val type: CbartApiContentType,
    val coverImage: String?,
    val title: String?,
    val username: String?,
    val uid: String?,
    val avatarUrl: String?,
    val priceDiamond: Int?,
    val priceGold: Int?,
    val isFree: Boolean,
    val likeCount: Int?,
    val commentCount: Int?,
)

internal enum class CbartApiContentType {
    Video, Picture, Fiction, Unknown,
}

// ==================== 用户设置/信息 ====================

@Serializable
internal data class CbartSettingResponse(
    val success: Int? = null,
    val data: CbartSettingData? = null,
)

@Serializable
internal data class CbartSettingData(
    @SerialName("invite_code")
    val inviteCode: String? = null,
    @SerialName("customer_service_info")
    val customerServiceInfo: String? = null,
)

// ==================== 内容列表（首页/用户页 HTML 解析产物） ====================

internal data class CbartContentItem(
    val id: String,
    val type: CbartApiContentType,
    val coverImage: String?,
    val thumbnailImages: List<String>,
    val title: String?,
    val username: String?,
    val uid: String?,
    val avatarUrl: String?,
    val priceDiamond: Int?,
    val priceGold: Int?,
    val isFree: Boolean,
)

// ==================== 用户关注操作 ====================

@Serializable
internal data class CbartFollowResponse(
    val success: Int? = null,
    val msg: String? = null,
)
