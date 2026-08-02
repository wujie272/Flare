package dev.dimension.flare.data.network.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * B站通用 API 响应包装
 */
@Serializable
public data class BilibiliResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
)

// ==================== 播放地址 ====================

@Serializable
public data class PlayUrlData(
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    @SerialName("accept_format")
    val acceptFormat: String = "",
    @SerialName("accept_description")
    val acceptDescription: List<String> = emptyList(),
    @SerialName("accept_quality")
    val acceptQuality: List<Int> = emptyList(),
    @SerialName("video_codecid")
    val videoCodecid: Int = 0,
    val durl: List<Durl>? = null,
    val dash: Dash? = null,
    @SerialName("support_formats")
    val supportFormats: List<FormatItem>? = null,
    @SerialName("last_play_time")
    val lastPlayTime: Int? = null,
    @SerialName("last_play_cid")
    val lastPlayCid: Long? = null,
    @SerialName("cur_language")
    val curLanguage: String? = null,
    @SerialName("dolby_type")
    val dolbyType: Int? = null,
    @SerialName("ai_audio")
    val aiAudio: AiAudioInfo? = null,
)

@Serializable
public data class Durl(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    @SerialName("backup_url")
    val backupUrl: List<String>? = null,
)

@Serializable
public data class Dash(
    val duration: Int = 0,
    val minBufferTime: Float = 0f,
    val video: List<DashStream> = emptyList(),
    val audio: List<DashStream>? = emptyList(),
    val dolby: Dolby? = null,
    val flac: Flac? = null,
)

@Serializable
public data class DashStream(
    val id: Int = 0,
    @SerialName("baseUrl")
    val baseUrl: String = "",
    @SerialName("backupUrl")
    val backupUrl: List<String>? = null,
    val bandwidth: Int = 0,
    @SerialName("mime_type")
    val mimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("frame_rate")
    val frameRate: String = "",
    @SerialName("start_with_sap")
    val startWithSap: Int? = null,
    @SerialName("segment_base")
    val segmentBase: SegmentBase? = null,
    val codecid: Int? = null,
)

@Serializable
public data class SegmentBase(
    val initialization: String? = null,
    @SerialName("index_range")
    val indexRange: String? = null,
)

@Serializable
public data class FormatItem(
    val quality: Int = 0,
    val format: String = "",
    @SerialName("new_description")
    val newDescription: String = "",
    @SerialName("display_desc")
    val displayDesc: String = "",
    val codecs: List<String>? = null,
)

@Serializable
public data class Dolby(
    val type: Int = 0,
    val audio: List<DashStream>? = null,
)

@Serializable
public data class Flac(
    val display: Boolean = false,
    val audio: DashStream? = null,
)

@Serializable
public data class AiAudioInfo(
    val title: String = "",
    val items: List<AiAudioItem> = emptyList(),
)

@Serializable
public data class AiAudioItem(
    @SerialName("lang_code")
    val langCode: String = "",
    @SerialName("lang_doc")
    val langDoc: String = "",
    @SerialName("stream_url")
    val streamUrl: String = "",
)

// ==================== 视频信息 ====================

@Serializable
public data class VideoInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    val pubdate: Long = 0,
    val owner: VideoOwner = VideoOwner(),
    val stat: VideoStat = VideoStat(),
    @SerialName("t_id")
    val tid: Int = 0,
    @SerialName("tname")
    val tname: String = "",
    val videos: Int = 0,
    val duration: Int = 0,
    @SerialName("first_frame")
    val firstFrame: String = "",
)

@Serializable
public data class VideoOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
public data class VideoStat(
    val view: Long = 0,
    val like: Long = 0,
    val coin: Long = 0,
    val favorite: Long = 0,
    val share: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
)

// ==================== 用户信息 ====================

@Serializable
public data class UserInfo(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0,
    val sex: String = "",
    val topPhoto: String = "",
    @SerialName("level_info")
    val levelInfo: LevelInfo? = null,
    @SerialName("official_verify")
    val officialVerify: OfficialVerify? = null,
    @SerialName("vip")
    val vip: VipInfo? = null,
)

@Serializable
public data class LevelInfo(
    @SerialName("current_level")
    val currentLevel: Int = 0,
    @SerialName("current_min")
    val currentMin: Int = 0,
    @SerialName("current_exp")
    val currentExp: Int = 0,
    @SerialName("next_exp")
    val nextExp: Int = 0,
)

@Serializable
public data class OfficialVerify(
    val type: Int = -1,
    val desc: String = "",
)

@Serializable
public data class VipInfo(
    val type: Int = 0,
    val status: Int = 0,
    @SerialName("due_date")
    val dueDate: Long = 0,
    @SerialName("vip_pay_type")
    val vipPayType: Int = 0,
    val themeType: Int = 0,
    val label: VipLabel? = null,
)

@Serializable
public data class VipLabel(
    val path: String = "",
    val text: String = "",
    @SerialName("label_theme")
    val labelTheme: String = "",
)

// ==================== 评论 ====================

@Serializable
public data class CommentData(
    val cursor: CommentCursor = CommentCursor(),
    val replies: List<CommentItem> = emptyList(),
    val upper: CommentUpper? = null,
)

@Serializable
public data class CommentCursor(
    val allCount: Int = 0,
    val isEnd: Boolean = true,
    val next: Int = 0,
    val prev: Int = 0,
    val mode: Int = 0,
)

@Serializable
public data class CommentItem(
    val rpid: Long = 0,
    val oid: Long = 0,
    val mid: Long = 0,
    val content: CommentContent = CommentContent(),
    val member: CommentMember = CommentMember(),
    val like: Long = 0,
    val rcount: Int = 0,
    val ctime: Long = 0,
    val replies: List<CommentItem>? = null,
    @SerialName("reply_control")
    val replyControl: CommentReplyControl? = null,
)

@Serializable
public data class CommentContent(
    val message: String = "",
    val members: List<CommentAtMember>? = null,
    val emote: Map<String, CommentEmote>? = null,
)

@Serializable
public data class CommentAtMember(
    val mid: Long = 0,
    @SerialName("uname")
    val uName: String = "",
)

@Serializable
public data class CommentEmote(
    val text: String = "",
    val url: String = "",
)

@Serializable
public data class CommentMember(
    val mid: String = "",
    @SerialName("uname")
    val uName: String = "",
    val avatar: String = "",
    val level: Int = 0,
    @SerialName("vip")
    val vip: VipInfo? = null,
    @SerialName("official_verify")
    val officialVerify: OfficialVerify? = null,
)

@Serializable
public data class CommentReplyControl(
    @SerialName("sub_reply_entry_text")
    val subReplyEntryText: String? = null,
    @SerialName("sub_reply_title_text")
    val subReplyTitleText: String? = null,
    @SerialName("time_desc")
    val timeDesc: String? = null,
)

@Serializable
public data class CommentUpper(
    val mid: Long = 0,
)

// ==================== 搜索 ====================

@Serializable
public data class SearchData(
    val numResults: Int = 0,
    val numPages: Int = 0,
    val result: List<SearchResult>? = null,
)

@Serializable
public data class SearchResult(
    val type: String = "",
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val author: String = "",
    val mid: Long = 0,
    val play: Long = 0,
    val videoReview: Long = 0,
    val pic: String = "",
    val duration: String = "",
    val pubdate: Long = 0,
    val description: String = "",
    val tag: String = "",
    val create: String = "",
    @SerialName("upic")
    val upic: String = "",
)

// ==================== 动态 ====================

@Serializable
public data class DynamicFeedData(
    val items: List<DynamicItem> = emptyList(),
    @SerialName("update_baseline")
    val updateBaseline: String = "",
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Serializable
public data class DynamicItem(
    val id: String = "",
    val type: String = "",
    val modules: DynamicModules = DynamicModules(),
)

@Serializable
public data class DynamicModules(
    @SerialName("module_author")
    val moduleAuthor: DynamicModuleAuthor = DynamicModuleAuthor(),
    @SerialName("module_dynamic")
    val moduleDynamic: DynamicModuleDynamic? = null,
    @SerialName("module_stat")
    val moduleStat: DynamicModuleStat? = null,
)

@Serializable
public data class DynamicModuleAuthor(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    @SerialName("pub_ts")
    val pubTs: Long = 0,
    @SerialName("pub_action")
    val pubAction: String = "",
)

@Serializable
public data class DynamicModuleDynamic(
    val type: String = "",
    val major: DynamicMajor? = null,
    val desc: DynamicDesc? = null,
)

@Serializable
public data class DynamicMajor(
    val type: String = "",
    val archive: DynamicArchive? = null,
    val draw: DynamicDraw? = null,
    val article: DynamicArticle? = null,
)

@Serializable
public data class DynamicArchive(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration: Long = 0,
    val stat: DynamicStat = DynamicStat(),
)

@Serializable
public data class DynamicStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0,
)

@Serializable
public data class DynamicDraw(
    val items: List<DynamicDrawItem> = emptyList(),
)

@Serializable
public data class DynamicDrawItem(
    val src: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
public data class DynamicArticle(
    val id: Long = 0,
    val title: String = "",
    val desc: String = "",
    val covers: List<String> = emptyList(),
    val pubTime: Long = 0,
)

@Serializable
public data class DynamicDesc(
    val text: String = "",
)

@Serializable
public data class DynamicModuleStat(
    val repost: Long = 0,
    val like: Long = 0,
    val reply: Long = 0,
)

// ==================== 通知 ====================

@Serializable
public data class FeedUnreadData(
    val at: Int = 0,
    val reply: Int = 0,
    val like: Int = 0,
    val sysMsg: Int = 0,
    val up: Int = 0,
    val chat: Int = 0,
    val coin: Int = 0,
    val danmu: Int = 0,
    val favorite: Int = 0,
    @SerialName("recv_like")
    val recvLike: Int = 0,
    @SerialName("recv_reply")
    val recvReply: Int = 0,
)

@Serializable
public data class NotificationItem(
    val id: Long = 0,
    val mid: Long = 0,
    val nickname: String = "",
    val avatar: String = "",
    val message: String = "",
    val ctime: Long = 0,
    val uri: String = "",
    val reply: NotificationReply? = null,
)

@Serializable
public data class NotificationReply(
    val rpid: Long = 0,
    val oid: Long = 0,
    val type: Int = 0,
    val mid: Long = 0,
    val root: Long = 0,
    val parent: Long = 0,
    val count: Int = 0,
    val rcount: Int = 0,
    val like: Long = 0,
    val ctime: Long = 0,
    val content: CommentContent? = null,
    val member: CommentMember? = null,
)

// ==================== 推荐 ====================

@Serializable
public data class RecommendFeedData(
    val item: List<RecommendItem> = emptyList(),
    @SerialName("business_card")
    val businessCard: String? = null,
)

@Serializable
public data class RecommendItem(
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: VideoOwner = VideoOwner(),
    val stat: VideoStat = VideoStat(),
    val rcmdReason: String? = null,
    @SerialName("first_frame")
    val firstFrame: String? = null,
)
