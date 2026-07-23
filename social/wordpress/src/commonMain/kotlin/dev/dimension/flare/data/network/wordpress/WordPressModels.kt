package dev.dimension.flare.data.network.wordpress

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WordPress REST API 文章列表响应
 * GET /wp-json/wp/v2/posts?page=1&per_page=20
 */
@Serializable
internal data class WPPost(
    val id: Long,
    val date: String? = null,
    @SerialName("date_gmt")
    val dateGmt: String? = null,
    val slug: String? = null,
    val link: String? = null,
    val title: WPTitle? = null,
    val content: WPContent? = null,
    val excerpt: WPExcerpt? = null,
    @SerialName("featured_media")
    val featuredMedia: Long? = null,
    val categories: List<Long>? = null,
    val tags: List<Long>? = null,
    @SerialName("_embedded")
    val embedded: WPEmbedded? = null,
)

@Serializable
internal data class WPTitle(
    val rendered: String? = null,
)

@Serializable
internal data class WPContent(
    val rendered: String? = null,
    val protected: Boolean? = null,
)

@Serializable
internal data class WPExcerpt(
    val rendered: String? = null,
    val protected: Boolean? = null,
)

/**
 * _embedded 中的媒体和作者信息
 */
@Serializable
internal data class WPEmbedded(
    @SerialName("wp:featuredmedia")
    val featuredMedia: List<WPMedia>? = null,
    val author: List<WPAuthor>? = null,
    @SerialName("wp:term")
    val terms: List<List<WPTerm>>? = null,
)

@Serializable
internal data class WPMedia(
    val id: Long? = null,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    @SerialName("media_details")
    val mediaDetails: WPMediaDetails? = null,
)

@Serializable
internal data class WPMediaDetails(
    val width: Int? = null,
    val height: Int? = null,
    val sizes: Map<String, WPMediaSize>? = null,
)

@Serializable
internal data class WPMediaSize(
    @SerialName("source_url")
    val sourceUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class WPAuthor(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("avatar_urls")
    val avatarUrls: Map<String, String>? = null,
)

@Serializable
internal data class WPTerm(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
    val taxonomy: String? = null,
)

/**
 * 分类列表响应
 * GET /wp-json/wp/v2/categories?per_page=100
 */
@Serializable
internal data class WPCategory(
    val id: Long,
    val name: String? = null,
    val slug: String? = null,
    val count: Int? = null,
    val description: String? = null,
    val parent: Long? = null,
)

/**
 * WordPress 站点信息探测响应
 * GET /wp-json/
 */
@Serializable
internal data class WPSiteInfo(
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val namespaces: List<String>? = null,
)
