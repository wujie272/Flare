package dev.dimension.flare.data.network.coolapk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class CoolapkResponse<T>(
    val data: T? = null,
    val status: Int = 0,
    val message: String? = null,
)

@Serializable
public data class CoolapkFeedItem(
    val id: Long = 0,
    val message: String = "",
    val username: String = "",
    val uid: Long = 0,
    @SerialName("userAvatar")
    val userAvatar: String = "",
    @SerialName("dateline")
    val createdAt: Long = 0,
    @SerialName("pic")
    val pic: String = "",
    @SerialName("feedTypeName")
    val type: String = "",
    @SerialName("blockInfo")
    val blockInfo: CoolapkBlockInfo? = null,
    @SerialName("likenum")
    val likeCount: Int = 0,
    @SerialName("replynum")
    val replyCount: Int = 0,
    @SerialName("favnum")
    val favoriteCount: Int = 0,
    @SerialName("sharenum")
    val shareCount: Int = 0,
    @SerialName("is_liked")
    val isLiked: Int = 0,
    @SerialName("entityTemplate")
    val entityTemplate: String = "",
    @SerialName("title")
    val title: String = "",
    @SerialName("url")
    val url: String = "",
    @SerialName("device_title")
    val deviceTitle: String = "",
    @SerialName("info")
    val info: String = "",
    @SerialName("infoHtml")
    val infoHtml: String = "",
)

@Serializable
public data class CoolapkBlockInfo(
    @SerialName("block_title")
    val title: String = "",
    @SerialName("block_content")
    val content: String = "",
    @SerialName("block_pic")
    val pic: String = "",
)

@Serializable
public data class CoolapkUser(
    val uid: Long = 0,
    val username: String = "",
    @SerialName("userAvatar")
    val avatar: String = "",
    @SerialName("verify_status")
    val verified: Int = 0,
    @SerialName("level")
    val level: Int = 0,
    @SerialName("fans")
    val followersCount: Int = 0,
    @SerialName("follow")
    val followingCount: Int = 0,
    @SerialName("feed")
    val feedCount: Int = 0,
    @SerialName("bio")
    val bio: String = "",
    @SerialName("cover")
    val cover: String = "",
)

@Serializable
public data class CoolapkLoginInfo(
    @SerialName("token")
    val token: String = "",
    @SerialName("uid")
    val uid: String = "",
    @SerialName("username")
    val username: String = "",
    @SerialName("avatar")
    val avatar: String = "",
)

/** 通知项 */
@Serializable
public data class CoolapkNotificationItem(
    val id: Long = 0,
    val uid: Long = 0,
    @SerialName("from_type")
    val fromType: Int = 0,
    @SerialName("fromuid")
    val fromUid: Long = 0,
    @SerialName("fromusername")
    val fromUsername: String = "",
    @SerialName("list_group")
    val listGroup: Int = 0,
    val type: String = "",
    val slug: String = "",
    val isnew: Int = 0,
    val note: String = "",
    @SerialName("dateline")
    val createdAt: Long = 0,
    @SerialName("entityType")
    val entityType: String = "",
    @SerialName("entityId")
    val entityId: String = "",
    val url: String = "",
    @SerialName("fromUserAvatar")
    val fromUserAvatar: String = "",
    @SerialName("fromUserInfo")
    val fromUserInfo: CoolapkUser? = null,
    @SerialName("notifyCount")
    val notifyCount: CoolapkNotifyCount? = null,
)

/** 通知未读计数 */
@Serializable
public data class CoolapkNotifyCount(
    val cloudInstall: Int = 0,
    val notification: Int = 0,
    @SerialName("contacts_follow")
    val contactsFollow: Int = 0,
    val message: Int = 0,
    val atme: Int = 0,
    val atcommentme: Int = 0,
    val commentme: Int = 0,
    val feedlike: Int = 0,
    val badge: Int = 0,
    @SerialName("notification_v18")
    val notificationV18: Int = 0,
    @SerialName("badge_v18")
    val badgeV18: Int = 0,
    val dateline: Long = 0,
)

/** 关注/粉丝列表中的关系项 */
@Serializable
public data class CoolapkFollowItem(
    val uid: Long = 0,
    val username: String = "",
    val fuid: Long = 0,
    val fusername: String = "",
    val isfriend: Int = 0,
    val dateline: Long = 0,
    @SerialName("entityType")
    val entityType: String = "",
    @SerialName("entityId")
    val entityId: String = "",
)
