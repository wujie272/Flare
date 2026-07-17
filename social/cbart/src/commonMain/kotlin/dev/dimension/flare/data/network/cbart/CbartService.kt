package dev.dimension.flare.data.network.cbart

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.network.cbart.api.*
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Cbart 统一服务层。
 *
 * 架构：
 * - [api] → CbartApiClient 调真实 `/api/` 端点
 * - HTML 刮削 → Ksoup 解析首页/收藏页等纯 PHP SSR 页面
 */
internal class CbartService(
    credentialFlow: Flow<CbartCredential>,
    onCredentialRefreshed: suspend (CbartCredential) -> Unit = {},
) {
    val api = CbartApiClient(credentialFlow = credentialFlow)

    private val credentialFlowRef = credentialFlow

    // ==================== 首页内容（HTML 刮削） ====================

    /**
     * 爬 `index.php`，提取所有内容卡片（super-block 结构）。
     * 首页是纯 PHP SSR，没有 JSON API，只能刮 HTML。
     */
    suspend fun fetchHomePage(): List<CbartContentItem> {
        val html = api.fetchIndexHtml()
        return parseSuperBlocks(html)
    }

    /**
     * 爬 `home.php?group_list=1`（专辑列表页），提取内容卡片
     */
    suspend fun fetchAlbumPage(): List<CbartContentItem> {
        val html = api.fetchHomeHtml()
        return parseSuperBlocks(html)
    }

    /**
     * 爬 `home.php?my_favorite=1`（收藏列表页）
     */
    suspend fun fetchFavorites(): List<CbartContentItem> {
        val html = api.fetchFavoritesHtml()
        return parseSuperBlocks(html)
    }

    /**
     * 用户个人页内容
     */
    suspend fun fetchUserContent(uid: String): List<CbartContentItem> {
        // TODO: 爬 home.php?uid=X 或 video_list.php?uid=X
        return emptyList()
    }

    // ==================== 用户信息 ====================

    /**
     * 从 API 获取当前登录用户信息
     *
     * 注意：Cbart API 没有提供获取当前用户 uid 的端点。
     * 如果 credential 中已存储 uid 则直接返回；
     * 否则用 sessionId 的前 8 位作为临时 uid，确保登录流程能完成。
     */
    suspend fun fetchCurrentUser(): CbartUserInfo? {
        val cred = currentCredential() ?: return null

        // 如果 credential 已有 uid 信息，直接返回
        if (cred.userId != null) {
            return CbartUserInfo(
                uid = cred.userId,
                username = cred.userName ?: "",
                nickName = cred.nickName ?: cred.userName ?: "",
                avatarUrl = cred.avatarUrl,
            )
        }

        // 首次登录时 uid 还未存储，用 sessionId 的前 8 位作为临时 uid
        // 用户后续使用时会从 credential 中获取到真实 uid
        val fallbackUid = "cbart_${cred.sessionId.take(8)}"
        return CbartUserInfo(
            uid = fallbackUid,
            username = "Cbart User",
            nickName = "Cbart User",
            avatarUrl = null,
        )
    }

    /**
     * 验证 session 是否有效
     */
    suspend fun validateSession(): Boolean {
        return api.getInviteCode()?.success == 1
    }

    suspend fun currentCredential(): CbartCredential? =
        credentialFlowRef.firstOrNull()

    // ==================== HTML 解析工具 ====================

    /**
     * 从 HTML 中解析所有 `.super-block` 卡片
     *
     * 首页/专辑列表页/收藏页共用同一套 DOM 结构：
     * ```html
     * <div class="super-block">
     *   <div class="super-block-up">
     *     <a href="...video_detail.php?id=X...">
     *       <div class="up-all" style="background-image:url(...)">
     *     </a>
     *   </div>
     *   <div class="super-block-down">
     *     <div class="block-down-detail">
     *       <div class="detail-line1">标题</div>
     *       <div class="detail-line2">
     *         <div class="line2-left">价格信息</div>
     *       </div>
     *       <div class="detail-line3">
     *         <div class="line3-left">作者名</div>
     *       </div>
     *     </div>
     *   </div>
     * </div>
     * ```
     */
    private fun parseSuperBlocks(html: String): List<CbartContentItem> {
        val doc = Ksoup.parse(html)
        val blocks = doc.select("div.super-block")
        if (blocks.isEmpty()) return emptyList()

        return blocks.mapNotNull { block ->
            try {
                // --- 提取链接和 ID ---
                val linkEl = block.select("div.super-block-up a").first()
                    ?: return@mapNotNull null
                val href = linkEl.attr("href")
                val (id, type) = parseHref(href) ?: return@mapNotNull null

                // --- 提取封面图 ---
                val upAll = block.select("div.up-all").first()
                val style = upAll?.attr("style") ?: ""
                val coverImage = extractBackgroundImage(style)

                // --- 提取标题 ---
                val detailLine1 = block.select("div.detail-line1").first()
                val title = detailLine1?.wholeText()?.trim()

                // --- 提取作者 ---
                val line3Left = block.select("div.detail-line3 div.line3-left").first()
                val authorText = line3Left?.wholeText()?.trim()
                val username = authorText?.removePrefix("Author: ")?.removePrefix("Studio ")
                    ?: authorText

                // --- 提取价格信息 ---
                val line2Left = block.select("div.detail-line2 div.line2-left").first()
                val priceText = line2Left?.wholeText()?.trim() ?: ""

                val priceDiamond = parseDiamondPrice(priceText)
                val priceGold = parseGoldPrice(priceText)
                val isFree = priceText.contains("Free", ignoreCase = true)

                CbartContentItem(
                    id = id,
                    type = type,
                    coverImage = coverImage,
                    thumbnailImages = emptyList(),
                    title = title,
                    username = username,
                    uid = null, // 首页列表不包含 uid
                    avatarUrl = null,
                    priceDiamond = priceDiamond,
                    priceGold = priceGold,
                    isFree = isFree,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 解析链接中的 ID 和内容类型
     *
     * video_detail.php?id=X → Video
     * group_pics.php?id=X → Picture
     */
    private fun parseHref(href: String): Pair<String, CbartApiContentType>? {
        val videoMatch = Regex("""video_detail\.php\?id=(\d+)""").find(href)
        if (videoMatch != null) {
            return videoMatch.groupValues[1] to CbartApiContentType.Video
        }
        val albumMatch = Regex("""group_pics\.php\?id=(\d+)""").find(href)
        if (albumMatch != null) {
            return albumMatch.groupValues[1] to CbartApiContentType.Picture
        }
        return null
    }

    /**
     * 从 style="background-image:url(...)" 中提取 URL
     */
    private fun extractBackgroundImage(style: String): String? {
        val match = Regex("""background-image\s*:\s*url\(['"]?(.*?)['"]?\)""", RegexOption.IGNORE_CASE)
            .find(style)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * 从价格文本中提取钻石价格
     * "Price：<i class="iconfont icon-zuanshi"></i>16Diamonds" → 16
     * "Free" → null
     */
    private fun parseDiamondPrice(text: String): Int? {
        val match = Regex("""(\d+)\s*Diamonds""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 从价格文本中提取金币价格
     */
    private fun parseGoldPrice(text: String): Int? {
        val match = Regex("""(\d+)\s*Gold""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}

internal data class CbartUserInfo(
    val uid: String,
    val username: String,
    val nickName: String,
    val avatarUrl: String?,
)
